ALTER TABLE learning_plans
    ADD COLUMN plan_mode VARCHAR(24) NOT NULL DEFAULT 'LONG_TERM',
    ADD COLUMN interview_date DATE,
    ADD COLUMN days_remaining INTEGER,
    ADD COLUMN request_json JSONB NOT NULL DEFAULT '{}'::jsonb;
