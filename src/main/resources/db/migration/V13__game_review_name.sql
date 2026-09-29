-- The name a reviewer chooses to show with their review on the website.
--
-- Nullable, because reviews written before this column existed have no name;
-- the website shows those as "Anonymous". The website asks for a name, but the
-- server still accepts a review without one, so a copy of the page that was
-- opened before this change can still send its review.
ALTER TABLE game_review
    ADD COLUMN reviewer_name VARCHAR(60);
