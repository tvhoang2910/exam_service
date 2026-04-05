-- Reporting moderation backfill for existing environments.
ALTER TABLE IF EXISTS questions
    ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN NOT NULL DEFAULT FALSE;
