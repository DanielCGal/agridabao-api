CREATE TABLE player_presence
(
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    connected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE friend_request
(
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMPTZ,
    CONSTRAINT chk_friend_request_users CHECK (requester_id <> receiver_id)
);
CREATE INDEX idx_friend_request_receiver_status ON friend_request(receiver_id, status, created_at DESC);
CREATE INDEX idx_friend_request_requester_status ON friend_request(requester_id, status, created_at DESC);

CREATE TABLE friendship
(
    user_low_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    user_high_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_low_id, user_high_id)
);
CREATE INDEX idx_friendship_high ON friendship(user_high_id);

CREATE TABLE chat_message
(
    id UUID PRIMARY KEY,
    sender_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    body VARCHAR(1000) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMPTZ,
    CONSTRAINT chk_chat_users CHECK (sender_id <> receiver_id)
);
CREATE INDEX idx_chat_pair_sent ON chat_message(sender_id, receiver_id, sent_at);
CREATE INDEX idx_chat_receiver_unread ON chat_message(receiver_id, read_at, sent_at DESC);

CREATE TABLE marketplace_listing
(
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    item_type VARCHAR(80) NOT NULL,
    quantity INTEGER NOT NULL,
    asking_price INTEGER NOT NULL,
    listing_fee INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    sold_at TIMESTAMPTZ,
    buyer_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT chk_market_quantity CHECK (quantity > 0),
    CONSTRAINT chk_market_price CHECK (asking_price >= 0),
    CONSTRAINT chk_market_fee CHECK (listing_fee >= 0)
);
CREATE INDEX idx_market_status_expiry ON marketplace_listing(status, expires_at);
CREATE INDEX idx_market_item_status ON marketplace_listing(item_type, status, created_at DESC);
CREATE INDEX idx_market_seller ON marketplace_listing(seller_id, status, created_at DESC);

CREATE TABLE trade_session
(
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    target_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    requester_offer JSONB NOT NULL DEFAULT '{"money":0,"items":[]}'::jsonb,
    target_offer JSONB NOT NULL DEFAULT '{"money":0,"items":[]}'::jsonb,
    requester_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    target_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    requester_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    target_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_trade_users CHECK (requester_id <> target_id)
);
CREATE INDEX idx_trade_requester_status ON trade_session(requester_id, status, updated_at DESC);
CREATE INDEX idx_trade_target_status ON trade_session(target_id, status, updated_at DESC);
