-- Interface and text size, so a player on a small phone or a large tablet can
-- scale the game's UI to something they can read and hit.
--
-- 1.0 is the size the game has always drawn at, so defaulting to it leaves every
-- existing account looking exactly as it does today. The bounds enforced by
-- PlayerSettingsService are 0.80-1.20 for the interface and 0.85-1.35 for text;
-- they are deliberately not CHECK constraints, because a tightened range in a
-- later build would then reject rows the previous build legitimately wrote.

ALTER TABLE player_settings
    ADD COLUMN ui_scale REAL NOT NULL DEFAULT 1,
    ADD COLUMN text_scale REAL NOT NULL DEFAULT 1;
