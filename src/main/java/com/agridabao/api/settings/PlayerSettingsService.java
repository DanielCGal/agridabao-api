package com.agridabao.api.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PlayerSettingsService {
    static final float DEFAULT_MUSIC = 0.6f;
    static final float DEFAULT_SFX = 0.9f;
    static final float DEFAULT_AMBIENCE = 0.5f;
    static final float DEFAULT_RENDER = 30f;

    /** 1 is the size the game has always drawn at, so this is "unchanged". */
    static final float DEFAULT_UI_SCALE = 1f;
    static final float DEFAULT_TEXT_SCALE = 1f;

    private static final float RENDER_MIN = 8f;
    private static final float RENDER_MAX = 60f;

    // Mirrors GameSettings on the Unity side. The interface ceiling is bounded by
    // the tallest panel still fitting once the canvas reference resolution shrinks;
    // the text ceiling is lower because an oversized label wraps inside its rect.
    private static final float UI_SCALE_MIN = 0.80f;
    private static final float UI_SCALE_MAX = 1.20f;
    private static final float TEXT_SCALE_MIN = 0.80f;
    private static final float TEXT_SCALE_MAX = 1.15f;

    private final PlayerSettingsRepository repository;

    public PlayerSettingsService(PlayerSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PlayerSettingsResponse get(UUID userId) {
        return repository.findById(userId)
                .map(PlayerSettingsResponse::from)
                .orElseGet(() -> new PlayerSettingsResponse(
                        DEFAULT_MUSIC, DEFAULT_SFX, DEFAULT_AMBIENCE, DEFAULT_RENDER,
                        DEFAULT_UI_SCALE, DEFAULT_TEXT_SCALE));
    }

    @Transactional
    public PlayerSettingsResponse upsert(UUID userId, PlayerSettingsRequest request) {
        float music = clamp01(request.musicVolume());
        float sfx = clamp01(request.sfxVolume());
        float ambience = clamp01(request.ambienceVolume());
        float render = clamp(request.renderDistance(), RENDER_MIN, RENDER_MAX);
        Instant now = Instant.now();

        PlayerSettings settings = repository.findById(userId).orElse(null);

        // A client that predates the scale fields sends neither, and Jackson leaves
        // an absent float at 0. Clamping that would silently rewrite the player's
        // interface to the minimum, so a non-positive value means "leave it alone":
        // keep what is stored, or the default for a brand-new row.
        float currentUiScale = settings != null ? settings.getUiScale() : DEFAULT_UI_SCALE;
        float currentTextScale = settings != null ? settings.getTextScale() : DEFAULT_TEXT_SCALE;

        float uiScale = request.uiScale() > 0f
                ? clamp(request.uiScale(), UI_SCALE_MIN, UI_SCALE_MAX)
                : currentUiScale;
        float textScale = request.textScale() > 0f
                ? clamp(request.textScale(), TEXT_SCALE_MIN, TEXT_SCALE_MAX)
                : currentTextScale;

        if (settings == null) {
            settings = new PlayerSettings(
                    userId, music, sfx, ambience, render, uiScale, textScale, now);
        } else {
            settings.update(music, sfx, ambience, render, uiScale, textScale, now);
        }

        return PlayerSettingsResponse.from(repository.save(settings));
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
