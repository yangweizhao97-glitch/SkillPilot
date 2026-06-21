ALTER TABLE interview_questions
    ADD COLUMN answer_outline JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN reference_answer TEXT,
    ADD COLUMN scoring_rubric JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN common_mistakes JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN follow_up_candidates JSONB NOT NULL DEFAULT '[]'::jsonb;
