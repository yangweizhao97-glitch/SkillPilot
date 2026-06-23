ALTER TABLE agent_tasks
    ADD COLUMN optional_steps JSONB NOT NULL DEFAULT '[]'::jsonb;
