ALTER TABLE learning_plans
    ADD COLUMN generation_status VARCHAR(32) NOT NULL DEFAULT 'READY';

ALTER TABLE interview_sessions
    ADD COLUMN processing_answer BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN processing_started_at TIMESTAMPTZ;
