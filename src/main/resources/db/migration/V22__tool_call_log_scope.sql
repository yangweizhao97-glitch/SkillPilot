ALTER TABLE tool_call_logs ADD COLUMN scope_type VARCHAR(32);
ALTER TABLE tool_call_logs ADD COLUMN scope_id BIGINT;

UPDATE tool_call_logs
SET scope_type = 'TASK', scope_id = task_id
WHERE task_id IS NOT NULL AND scope_type IS NULL;

CREATE INDEX idx_tool_call_logs_scope ON tool_call_logs(scope_type, scope_id);
