-- Ratings and comments left on the promotional website.
--
-- Visitors do not sign in to review the game, so a review belongs to no
-- account and holds no personal data: the star rating, the comment and when it
-- was written. Nothing else references this table, so it cannot affect the game.
--
-- "hidden" is how an abusive comment is taken down without deleting it: set it
-- to TRUE from the Railway database view and the review drops out of the list
-- and out of the average on the website's next load.
CREATE TABLE game_review
(
    id         UUID PRIMARY KEY,
    rating     INTEGER       NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    hidden     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_game_review_rating CHECK (rating BETWEEN 1 AND 5)
);

-- The website asks for the newest visible reviews, and for how many visible
-- reviews gave each star rating.
CREATE INDEX idx_game_review_visible_created ON game_review (hidden, created_at DESC);
CREATE INDEX idx_game_review_visible_rating ON game_review (hidden, rating);
