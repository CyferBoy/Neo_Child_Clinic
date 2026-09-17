-- Cloudflare D1 schema for the Neo Child Clinic backup worker.
-- Deliberately holds only metadata about backups - never any clinic/patient data, which
-- lives solely (encrypted) in the R2 object itself.

CREATE TABLE IF NOT EXISTS backups (
    backup_id      TEXT PRIMARY KEY,
    account_id     TEXT NOT NULL,          -- Supabase auth user id (JWT `sub`) that owns this backup
    storage_path   TEXT NOT NULL,          -- R2 object key: backups/{account_id}/{backup_id}/backup.nccb
    size_bytes     INTEGER NOT NULL,
    app_version    TEXT NOT NULL,
    backup_version INTEGER NOT NULL,
    created_at     TEXT NOT NULL,          -- ISO-8601, as reported by the app at export time
    confirmed_at   TEXT NOT NULL,          -- when the Worker verified the R2 object post-upload
    status         TEXT NOT NULL DEFAULT 'CONFIRMED'
);

CREATE INDEX IF NOT EXISTS idx_backups_account_created ON backups(account_id, created_at DESC);
