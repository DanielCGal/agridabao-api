package com.agridabao.api.settings;

public record PlayerSettingsResponse(
        float musicVolume,
        float sfxVolume,
        float ambienceVolume,
        float renderDistance
) {
    public static PlayerSettingsResponse from(PlayerSettings settings) {
        return new PlayerSettingsResponse(
                settings.getMusicVolume(),
                settings.getSfxVolume(),
                settings.getAmbienceVolume(),
                settings.getRenderDistance()
        );
    }
}
