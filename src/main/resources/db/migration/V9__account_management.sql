-- Account self-service: forgot password, change email, change display name.

-- A password-reset or email-change code belongs to a known account, unlike the
-- sign-up and login codes which are keyed on the address alone. For an email
-- change the "email" column holds the NEW address (that is where the code is
-- sent), so the owner has to be recorded separately or the confirmation step
-- would have no way to tell whose address is being moved.
ALTER TABLE email_verification
    ADD COLUMN user_id UUID;

CREATE INDEX idx_email_verification_user_purpose
    ON email_verification (user_id, purpose);

-- Signing in with a display name only works if a display name identifies one
-- account. Nothing enforced that until now, so any existing collisions are
-- broken apart first: the oldest account keeps the name and every later one
-- gets a short suffix from its own id. The id fragment is used rather than a
-- counter because a counter can land on a name that is already taken, which
-- would fail the index below.
UPDATE app_user u
SET display_name = left(u.display_name, 71) || '-' || substr(replace(u.id::text, '-', ''), 1, 8),
    updated_at   = CURRENT_TIMESTAMP
WHERE u.display_name IS NOT NULL
  AND btrim(u.display_name) <> ''
  AND EXISTS (SELECT 1
              FROM app_user o
              WHERE o.id <> u.id
                AND o.display_name IS NOT NULL
                AND lower(o.display_name) = lower(u.display_name)
                AND (o.created_at < u.created_at
                  OR (o.created_at = u.created_at AND o.id < u.id)));

-- Case-insensitive, because a player typing "Juan" must reach the account
-- registered as "juan". Partial, because a display name is still optional and
-- several accounts may legitimately have none.
--
-- Wrapped so that a collision the rename above did not foresee cannot fail the
-- migration. A failed migration stops the whole API from starting, which takes
-- the game offline for everyone; a missing index costs far less than that.
-- AuthService looks names up with findFirst and tolerates a duplicate, and
-- AccountService refuses to create new ones, so the app stays correct either
-- way - this index is the belt to those braces, not the only guard.
DO
$$
    BEGIN
        CREATE UNIQUE INDEX ux_app_user_display_name_lower
            ON app_user (lower(display_name))
            WHERE display_name IS NOT NULL AND btrim(display_name) <> '';
    EXCEPTION
        WHEN unique_violation THEN
            RAISE WARNING 'Duplicate display names remain; ux_app_user_display_name_lower not created.';
        WHEN duplicate_table THEN
            RAISE NOTICE 'ux_app_user_display_name_lower already exists.';
    END
$$;
