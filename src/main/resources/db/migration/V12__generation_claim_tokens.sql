ALTER TABLE learning_plans
    ADD COLUMN generation_id VARCHAR(64);

ALTER TABLE interview_sessions
    ADD COLUMN processing_message_id BIGINT;
