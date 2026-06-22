ALTER TABLE public_interview_questions
    ADD COLUMN quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN quality_score INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN confidence_label VARCHAR(40) NOT NULL DEFAULT 'SINGLE_SOURCE',
    ADD COLUMN quality_review JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN quality_reviewed_at TIMESTAMPTZ;

ALTER TABLE public_interview_questions
    ADD CONSTRAINT chk_public_question_quality_score CHECK (quality_score BETWEEN 0 AND 100);

CREATE INDEX idx_public_questions_quality_status
    ON public_interview_questions(quality_status, quality_score);

CREATE TABLE public_question_evidence (
    id BIGSERIAL PRIMARY KEY,
    question_hash VARCHAR(64) NOT NULL,
    source_id BIGINT NOT NULL REFERENCES knowledge_sources(id) ON DELETE CASCADE,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(question_hash, source_id)
);

CREATE INDEX idx_public_question_evidence_hash ON public_question_evidence(question_hash);

CREATE UNIQUE INDEX uq_knowledge_sources_source_url
    ON knowledge_sources((LOWER(source_url))) WHERE source_url IS NOT NULL;

INSERT INTO public_question_evidence(question_hash, source_id)
SELECT DISTINCT q.question_hash, e.source_id
FROM public_interview_questions q
JOIN interview_experiences e ON e.id = q.experience_id
ON CONFLICT DO NOTHING;
