-- Four more questions on the website's review form: which game features the
-- reviewer enjoys most, a 1 to 5 rating of the website's design, which website
-- section was hardest to use, and one thing they would change about the website.
--
-- Every column is nullable, because reviews written before these questions
-- existed have no answers, and a copy of the page opened before this change
-- still sends only a name, a rating and a comment.
--
-- The two multiple-choice answers are stored as comma-separated codes in the
-- order the form lists them (for example FARM_LAND,AI_ADVISER,OTHER). The text a
-- reviewer types beside "Other" has its own column.
ALTER TABLE game_review
    ADD COLUMN enjoyed_features VARCHAR(400);

ALTER TABLE game_review
    ADD COLUMN enjoyed_other VARCHAR(100);

ALTER TABLE game_review
    ADD COLUMN website_rating INTEGER;

ALTER TABLE game_review
    ADD COLUMN hardest_sections VARCHAR(300);

ALTER TABLE game_review
    ADD COLUMN hardest_other VARCHAR(100);

ALTER TABLE game_review
    ADD COLUMN website_change VARCHAR(1000);

ALTER TABLE game_review
    ADD CONSTRAINT chk_game_review_website_rating
        CHECK (website_rating IS NULL OR website_rating BETWEEN 1 AND 5);
