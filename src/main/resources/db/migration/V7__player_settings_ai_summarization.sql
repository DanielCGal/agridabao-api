-- Whether the farm adviser and the climate evaluation answer briefly.
--
-- FALSE is the behaviour the game has always had - a full write-up with bullet
-- points - so every existing account reads exactly as it does today until the
-- player turns this on themselves. Only those two features are affected; task
-- generation and grading must return strict JSON and are never shortened.

ALTER TABLE player_settings
    ADD COLUMN ai_summarization BOOLEAN NOT NULL DEFAULT FALSE;
