-- Lets a seller be told, once, that one of their listings sold.
--
-- buyer_id already exists, so only the "has the seller seen this" flag is new.
-- Existing sold listings are marked acknowledged so nobody is greeted by a burst
-- of notifications for sales that happened before this feature existed.

ALTER TABLE marketplace_listing
    ADD COLUMN sale_acknowledged BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE marketplace_listing
SET sale_acknowledged = TRUE
WHERE status = 'SOLD';

-- Supports the "unseen sales for this seller" lookup.
CREATE INDEX IF NOT EXISTS idx_marketplace_listing_seller_unacked
    ON marketplace_listing (seller_id, status, sale_acknowledged);
