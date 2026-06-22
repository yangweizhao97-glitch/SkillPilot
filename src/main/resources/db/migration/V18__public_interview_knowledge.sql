CREATE TABLE knowledge_sources (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    platform VARCHAR(64),
    source_url VARCHAR(1000),
    title VARCHAR(255) NOT NULL,
    published_at TIMESTAMPTZ,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    content_hash VARCHAR(64) NOT NULL UNIQUE,
    copyright_status VARCHAR(32) NOT NULL,
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_score NUMERIC(5, 4) NOT NULL DEFAULT 0.5000,
    created_by BIGINT REFERENCES users(id),
    reviewed_by BIGINT REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    rejection_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_knowledge_source_quality CHECK (quality_score >= 0 AND quality_score <= 1)
);

CREATE INDEX idx_knowledge_sources_review_status ON knowledge_sources(review_status);
CREATE INDEX idx_knowledge_sources_platform ON knowledge_sources(platform);

CREATE TABLE interview_experiences (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES knowledge_sources(id) ON DELETE CASCADE,
    industry VARCHAR(120),
    company VARCHAR(255),
    position VARCHAR(255) NOT NULL,
    experience_level VARCHAR(120),
    interview_round VARCHAR(120),
    summary TEXT NOT NULL,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    event_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_interview_experiences_source ON interview_experiences(source_id);
CREATE INDEX idx_interview_experiences_filters
    ON interview_experiences(industry, position, company, experience_level, interview_round, status);

CREATE TABLE public_interview_questions (
    id BIGSERIAL PRIMARY KEY,
    experience_id BIGINT NOT NULL REFERENCES interview_experiences(id) ON DELETE CASCADE,
    normalized_question TEXT NOT NULL,
    question_hash VARCHAR(64) NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    difficulty VARCHAR(32) NOT NULL,
    knowledge_points JSONB NOT NULL DEFAULT '[]'::jsonb,
    answer_outline JSONB NOT NULL DEFAULT '[]'::jsonb,
    reference_answer TEXT,
    scoring_rubric JSONB NOT NULL DEFAULT '[]'::jsonb,
    common_mistakes JSONB NOT NULL DEFAULT '[]'::jsonb,
    follow_up_candidates JSONB NOT NULL DEFAULT '[]'::jsonb,
    embedding vector(1024),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(experience_id, question_hash)
);

CREATE INDEX idx_public_interview_questions_experience ON public_interview_questions(experience_id);
CREATE INDEX idx_public_interview_questions_status ON public_interview_questions(status);
CREATE INDEX idx_public_interview_questions_hash ON public_interview_questions(question_hash);
