package com.agridabao.api.settings;

public record PlayerSettingsResponse(
        float musicVolume,
        float sfxVolume,
        float ambienceVolume,
        float renderDistance,
        float uiScale,
        float textScale
) {
    public static PlayerSettingsResponse from(PlayerSettings settings) {
        return new PlayerSettingsResponse(
                settings.getMusicVolume(),
                settings.getSfxVolume(),
                settings.getAmbienceVolume(),
                settings.getRenderDistance(),
                settings.getUiScale(),
                settings.getTextScale()
        );
    }
}
