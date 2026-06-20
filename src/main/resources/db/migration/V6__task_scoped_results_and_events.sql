ALTER TABLE job_match_reports ADD COLUMN task_id BIGINT REFERENCES agent_tasks(id);
ALTER TABLE resume_analysis_reports ADD COLUMN task_id BIGINT REFERENCES agent_tasks(id);

-- Existing rows predate task-scoped aggregation and stay nullable; every new result is task-bound.
CREATE UNIQUE INDEX uq_job_match_reports_task_id ON job_match_reports(task_id) WHERE task_id IS NOT NULL;
CREATE UNIQUE INDEX uq_resume_analysis_reports_task_id ON resume_analysis_reports(task_id) WHERE task_id IS NOT NULL;
CREATE INDEX idx_job_match_reports_task_id ON job_match_reports(task_id);
CREATE INDEX idx_resume_analysis_reports_task_id ON resume_analysis_reports(task_id);

ALTER TABLE tool_call_logs ADD COLUMN tool_call_id VARCHAR(36);
UPDATE tool_call_logs SET tool_call_id = gen_random_uuid()::text WHERE tool_call_id IS NULL;
ALTER TABLE tool_call_logs ALTER COLUMN tool_call_id SET NOT NULL;
CREATE UNIQUE INDEX uq_tool_call_logs_tool_call_id ON tool_call_logs(tool_call_id);
