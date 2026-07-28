package com.agridabao.api.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_settings")
public class PlayerSettings {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "music_volume", nullable = false)
    private float musicVolume;

    @Column(name = "sfx_volume", nullable = false)
    private float sfxVolume;

    @Column(name = "ambience_volume", nullable = false)
    private float ambienceVolume;

    @Column(name = "render_distance", nullable = false)
    private float renderDistance;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlayerSettings() {
    }

    public PlayerSettings(UUID userId, float musicVolume, float sfxVolume,
                          float ambienceVolume, float renderDistance, Instant updatedAt) {
        this.userId = userId;
        this.musicVolume = musicVolume;
        this.sfxVolume = sfxVolume;
        this.ambienceVolume = ambienceVolume;
        this.renderDistance = renderDistance;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() { return userId; }
    public float getMusicVolume() { return musicVolume; }
    public float getSfxVolume() { return sfxVolume; }
    public float getAmbienceVolume() { return ambienceVolume; }
    public float getRenderDistance() { return renderDistance; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(float musicVolume, float sfxVolume, float ambienceVolume,
                       float renderDistance, Instant updatedAt) {
        this.musicVolume = musicVolume;
        this.sfxVolume = sfxVolume;
        this.ambienceVolume = ambienceVolume;
        this.renderDistance = renderDistance;
        this.updatedAt = updatedAt;
    }
}
