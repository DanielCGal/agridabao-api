CREATE TABLE player_settings
(
    user_id          UUID PRIMARY KEY
                     REFERENCES app_user(id)
                     ON DELETE CASCADE,

    music_volume     REAL NOT NULL DEFAULT 0.6,
    sfx_volume       REAL NOT NULL DEFAULT 0.9,
    ambience_volume  REAL NOT NULL DEFAULT 0.5,
    render_distance  REAL NOT NULL DEFAULT 30,

    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
