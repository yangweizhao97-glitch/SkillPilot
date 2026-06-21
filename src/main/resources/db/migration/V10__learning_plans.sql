CREATE TABLE learning_plans (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    task_id BIGINT NOT NULL REFERENCES agent_tasks(id),
    report_id BIGINT NOT NULL REFERENCES final_reports(id),
    result_json JSONB NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_learning_plans_user_task UNIQUE (user_id, task_id)
);

CREATE INDEX idx_learning_plans_user_created ON learning_plans(user_id, created_at DESC);
