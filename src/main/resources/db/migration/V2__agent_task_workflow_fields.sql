ALTER TABLE agent_tasks
    ADD COLUMN enabled_steps JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
