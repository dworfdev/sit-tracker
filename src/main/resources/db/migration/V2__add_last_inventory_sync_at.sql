-- Tracks when each user's inventory was last synced from Steam, enforced
-- server-side so the sync cooldown can't be bypassed by refreshing the page,
-- using a different device, or calling the API directly.
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_inventory_sync_at TIMESTAMP;