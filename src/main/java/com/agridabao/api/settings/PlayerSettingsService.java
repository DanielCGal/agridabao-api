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

    private static final float RENDER_MIN = 8f;
    private static final float RENDER_MAX = 60f;

    private final PlayerSettingsRepository repository;

    public PlayerSettingsService(PlayerSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PlayerSettingsResponse get(UUID userId) {
        return repository.findById(userId)
                .map(PlayerSettingsResponse::from)
                .orElseGet(() -> new PlayerSettingsResponse(
                        DEFAULT_MUSIC, DEFAULT_SFX, DEFAULT_AMBIENCE, DEFAULT_RENDER));
    }

    @Transactional
    public PlayerSettingsResponse upsert(UUID userId, PlayerSettingsRequest request) {
        float music = clamp01(request.musicVolume());
        float sfx = clamp01(request.sfxVolume());
        float ambience = clamp01(request.ambienceVolume());
        float render = clamp(request.renderDistance(), RENDER_MIN, RENDER_MAX);
        Instant now = Instant.now();

        PlayerSettings settings = repository.findById(userId).orElse(null);
        if (settings == null) {
            settings = new PlayerSettings(userId, music, sfx, ambience, render, now);
        } else {
            settings.update(music, sfx, ambience, render, now);
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
