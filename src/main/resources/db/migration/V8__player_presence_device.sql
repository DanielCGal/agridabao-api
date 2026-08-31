-- Which device currently holds an account's play session.
--
-- Presence already tracked whether an account was playing; it could not say
-- where from, so two phones signed into one account both reported "online" and
-- neither knew about the other. Recording the device turns the same row into a
-- claim: the holder keeps refreshing it, and a different device is refused
-- while it stays fresh.
--
-- Nullable because rows written before this column existed have no device to
-- name. PresenceService treats a null holder as unclaimed, so the first
-- heartbeat after deploy takes ownership rather than locking anyone out.

ALTER TABLE player_presence
    ADD COLUMN device_id VARCHAR(128);
