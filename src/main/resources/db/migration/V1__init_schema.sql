-- Table: users
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY,
    steam_id VARCHAR(64),
    is_premium BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: tracked_items
CREATE TABLE IF NOT EXISTS tracked_items (
    id BIGSERIAL PRIMARY KEY,
    market_hash_name VARCHAR(255) NOT NULL UNIQUE,
    icon_url TEXT,
    current_price NUMERIC(10, 2),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: user_inventory (Aligned Surrogate Key + Composite Unique Constraint)
CREATE TABLE IF NOT EXISTS user_inventory (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL REFERENCES tracked_items(id) ON DELETE CASCADE,
    amount INT NOT NULL DEFAULT 1,
    is_monitored BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_user_item UNIQUE (user_id, item_id)
);

-- Table: price_history
CREATE TABLE IF NOT EXISTS price_history (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES tracked_items(id) ON DELETE CASCADE,
    price NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- B-Tree Performance Indexes
CREATE INDEX IF NOT EXISTS idx_tracked_items_market_hash ON tracked_items(market_hash_name);
CREATE INDEX IF NOT EXISTS idx_user_inventory_user_monitored ON user_inventory(user_id, is_monitored);
CREATE INDEX IF NOT EXISTS idx_price_history_item_created ON price_history(item_id, created_at DESC);