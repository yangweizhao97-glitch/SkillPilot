CREATE TABLE career_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_roles JSONB NOT NULL DEFAULT '[]'::jsonb,
    career_stages JSONB NOT NULL DEFAULT '[]'::jsonb,
    weakness_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    preference_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    summary TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_career_profiles_user UNIQUE(user_id)
);

CREATE INDEX idx_career_profiles_user_updated ON career_profiles(user_id, updated_at DESC);
