-- Lets a developer push an event or a payment to one player mid-session.

-- Alpha testers need to meet a typhoon and a pest outbreak inside a short
-- session, and both are normally rolled at random over in-game months. This
-- table is the queue that carries a hand-picked one to a named player: the
-- admin writes a row, that player's game collects it on its next poll and
-- applies it locally.
--
-- Delivery is deliberately pull rather than push. The game already polls this
-- server every fifteen seconds and holds the authoritative copy of the farm
-- while it is being played, so a command applied by the client lands in the
-- same state the player is looking at. Writing the change into the stored
-- snapshot instead would be overwritten by that client's next save.
CREATE TABLE admin_command
(
    id             UUID PRIMARY KEY,
    target_user_id UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,

    -- Kept for the audit trail. SET NULL rather than CASCADE so deleting the
    -- developer's own account does not erase the record of what they sent.
    issued_by      UUID        REFERENCES app_user (id) ON DELETE SET NULL,

    command_type   VARCHAR(32) NOT NULL,

    -- Which weather or which pest, by enum name. Null for a money grant.
    payload        VARCHAR(64),

    -- Pesos for a grant, days for a weather event. Null where not meaningful.
    amount         INTEGER,
    duration_days  INTEGER,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Stamped when the player's game collects it. Null means still waiting.
    delivered_at   TIMESTAMPTZ
);

-- The only query that runs often: "anything waiting for this player?"
CREATE INDEX idx_admin_command_pending
    ON admin_command (target_user_id, delivered_at, created_at);
