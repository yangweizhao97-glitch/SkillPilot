ALTER TABLE interview_questions
    ADD COLUMN task_id BIGINT REFERENCES agent_tasks(id);

CREATE INDEX idx_interview_questions_task_id ON interview_questions(task_id);

