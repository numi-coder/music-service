-- Per-store timezone enables wall-clock jingle scheduling across countries.
ALTER TABLE stores
    ADD COLUMN zone_id VARCHAR(64) NOT NULL DEFAULT 'Asia/Almaty';

-- Audit timestamps and jingle slot play times are now persisted as absolute
-- instants (UTC). Existing rows were written as Asia/Almaty wall-clock values,
-- so reinterpret them as Almaty-local and store the equivalent UTC instant,
-- preserving the moment each row represents.
--
-- NOTE: this assumes existing timestamps were produced by a host running in
-- Asia/Almaty local time. If this database was instead populated by a server
-- already running in UTC, the rows are correct as-is — skip this migration's
-- UPDATE statements (or re-stamp without the AT TIME ZONE conversion).
--
-- email_verification_tokens.expiry_date is intentionally left untouched: it was
-- already persisted as a UTC Instant.

UPDATE jingle_slots
SET created_at = created_at AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC',
    play_time  = play_time  AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC';

UPDATE jingles
SET created_at = created_at AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC';

UPDATE jingle_schedules
SET created_at = created_at AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC',
    updated_at = updated_at AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC';

UPDATE organizations
SET created_at = created_at AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC',
    updated_at = updated_at AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC';

UPDATE stores
SET created_at = created_at AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC',
    updated_at = updated_at AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC';

UPDATE users
SET created_at = created_at AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC',
    updated_at = updated_at AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC';

UPDATE outgoing_requests
SET created_at = created_at AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC';

UPDATE email_verification_tokens
SET created_at = created_at AT TIME ZONE 'Asia/Almaty' AT TIME ZONE 'UTC';
