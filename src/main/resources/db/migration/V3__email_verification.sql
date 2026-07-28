CREATE TABLE email_verification
(
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL,
    purpose         VARCHAR(20)  NOT NULL,   -- SIGNUP or LOGIN
    code_hash       VARCHAR(255) NOT NULL,   -- BCrypt hash of the emailed code

    -- Pending sign-up payload (held until the code is verified; NULL for LOGIN).
    password_hash   VARCHAR(255),
    display_name    VARCHAR(80),
    date_of_birth   DATE,

    attempts        INTEGER NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ NOT NULL,
    consumed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_verification_email_purpose ON email_verification (email, purpose);
