package com.agridabao.api.settings;

/**
 * @param uiScale   interface size multiplier. A client built before this field
 *                  existed omits it, and Jackson leaves an absent float at 0;
 *                  the service reads any non-positive value as "not supplied"
 *                  and keeps the stored value rather than clamping it to the
 *                  minimum, which would shrink the player's UI behind their back.
 * @param textScale text size multiplier, on top of {@code uiScale}. Same rule.
 */
public record PlayerSettingsRequest(
        float musicVolume,
        float sfxVolume,
        float ambienceVolume,
        float renderDistance,
        float uiScale,
        float textScale
) {
}
