-- Per-store timezone was dropped: the app runs in a single zone (JVM default).
-- Absolute timestamps stay as UTC Instant; only the unused store column is removed.
ALTER TABLE stores
    DROP COLUMN IF EXISTS zone_id;
