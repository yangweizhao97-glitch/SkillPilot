CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE uploaded_files (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    size_bytes BIGINT NOT NULL,
    parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    parsed_text TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_uploaded_files_user_id ON uploaded_files(user_id);

CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    file_id BIGINT REFERENCES uploaded_files(id),
    doc_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content_text TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_user_id ON documents(user_id);
CREATE INDEX idx_documents_doc_type ON documents(doc_type);

CREATE TABLE document_chunks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    document_id BIGINT NOT NULL REFERENCES documents(id),
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    token_count INT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_title VARCHAR(255) NOT NULL,
    source_locator VARCHAR(255),
    embedding vector(1024),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(document_id, chunk_index)
);

CREATE INDEX idx_document_chunks_user_id ON document_chunks(user_id);
CREATE INDEX idx_document_chunks_document_id ON document_chunks(document_id);
CREATE INDEX idx_document_chunks_source_type ON document_chunks(source_type);

CREATE TABLE resumes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    document_id BIGINT NOT NULL REFERENCES documents(id),
    title VARCHAR(255) NOT NULL,
    latest_analysis_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_resumes_user_id ON resumes(user_id);

CREATE TABLE jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    document_id BIGINT REFERENCES documents(id),
    company VARCHAR(255),
    position VARCHAR(255) NOT NULL,
    jd_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jobs_user_id ON jobs(user_id);

CREATE TABLE agent_tasks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    trace_id VARCHAR(80) NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    resume_id BIGINT REFERENCES resumes(id),
    job_id BIGINT REFERENCES jobs(id),
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agent_tasks_user_id ON agent_tasks(user_id);
CREATE INDEX idx_agent_tasks_trace_id ON agent_tasks(trace_id);

CREATE TABLE agent_execution_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    task_id BIGINT REFERENCES agent_tasks(id),
    trace_id VARCHAR(80) NOT NULL,
    agent_name VARCHAR(120) NOT NULL,
    step_name VARCHAR(120) NOT NULL,
    input_summary TEXT,
    output_summary TEXT,
    status VARCHAR(32) NOT NULL,
    duration_ms BIGINT,
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agent_execution_logs_task_id ON agent_execution_logs(task_id);
CREATE INDEX idx_agent_execution_logs_trace_id ON agent_execution_logs(trace_id);

CREATE TABLE tool_call_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    task_id BIGINT REFERENCES agent_tasks(id),
    trace_id VARCHAR(80) NOT NULL,
    agent_name VARCHAR(120) NOT NULL,
    tool_name VARCHAR(120) NOT NULL,
    input JSONB,
    output JSONB,
    status VARCHAR(32) NOT NULL,
    duration_ms BIGINT,
    error_message TEXT,
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tool_call_logs_task_id ON tool_call_logs(task_id);
CREATE INDEX idx_tool_call_logs_trace_id ON tool_call_logs(trace_id);

CREATE TABLE job_match_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    resume_id BIGINT NOT NULL REFERENCES resumes(id),
    job_id BIGINT NOT NULL REFERENCES jobs(id),
    version INT NOT NULL,
    result_json JSONB NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(resume_id, job_id, version)
);

CREATE INDEX idx_job_match_reports_user_id ON job_match_reports(user_id);

CREATE TABLE resume_analysis_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    resume_id BIGINT NOT NULL REFERENCES resumes(id),
    version INT NOT NULL,
    result_json JSONB NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(resume_id, version)
);

CREATE INDEX idx_resume_analysis_reports_user_id ON resume_analysis_reports(user_id);

CREATE TABLE interview_questions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    resume_id BIGINT NOT NULL REFERENCES resumes(id),
    job_id BIGINT NOT NULL REFERENCES jobs(id),
    question_text TEXT NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    difficulty VARCHAR(32) NOT NULL,
    expected_points JSONB NOT NULL DEFAULT '[]'::jsonb,
    citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_interview_questions_user_id ON interview_questions(user_id);
CREATE INDEX idx_interview_questions_resume_job ON interview_questions(resume_id, job_id);

CREATE TABLE final_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    task_id BIGINT REFERENCES agent_tasks(id),
    resume_id BIGINT NOT NULL REFERENCES resumes(id),
    job_id BIGINT NOT NULL REFERENCES jobs(id),
    version INT NOT NULL,
    report_json JSONB NOT NULL,
    export_status VARCHAR(32) NOT NULL DEFAULT 'NOT_EXPORTED',
    export_path VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(resume_id, job_id, version)
);

CREATE INDEX idx_final_reports_user_id ON final_reports(user_id);
