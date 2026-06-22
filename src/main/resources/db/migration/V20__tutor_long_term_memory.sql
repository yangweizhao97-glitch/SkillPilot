ALTER TABLE tutor_sessions
    ADD COLUMN memory_summary TEXT NOT NULL DEFAULT '',
    ADD COLUMN memory_through_sequence INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN memory_revision INTEGER NOT NULL DEFAULT 0;

