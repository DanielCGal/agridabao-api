-- Lets a password change end every session the account already has.

-- Access tokens are stateless: the server hands one out, keeps no record of it,
-- and afterwards only checks the signature and the expiry. That is what lets a
-- returning player skip the login screen, but it also means there is no list of
-- live sessions to clear, so changing a password left anyone already signed in
-- exactly where they were - for up to the full thirty days.
--
-- This counter is the missing handle. Every token carries the value the account
-- had when it was issued, the server compares the two on each request, and
-- changing a password increments it - which makes every token issued before that
-- moment stop matching, everywhere at once.

-- Zero rather than one, deliberately. Tokens issued before this column existed
-- carry no version at all and are read as zero, so they keep working and nobody
-- is signed out by the deployment itself. The first password change on an
-- account still moves it to one and invalidates every one of them.
ALTER TABLE app_user
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
