CREATE TABLE app_user
(
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(80),
    date_of_birth   DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE farm_save
(
    user_id             UUID PRIMARY KEY
                        REFERENCES app_user(id)
                        ON DELETE CASCADE,

    revision            BIGINT NOT NULL DEFAULT 0,
    schema_version      INTEGER NOT NULL,
    generator_version   VARCHAR(80) NOT NULL,
    snapshot            JSONB NOT NULL,
    saved_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_logout_at      TIMESTAMPTZ
);

CREATE INDEX idx_app_user_email ON app_user(email);
