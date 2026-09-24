package com.agridabao.api.settings;

public record PlayerSettingsRequest(
        float musicVolume,
        float sfxVolume,
        float ambienceVolume,
        float renderDistance,
        float uiScale,
        float textScale,
        boolean aiSummarization
) {
}
