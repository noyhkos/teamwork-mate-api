-- V3: analysis outputs — role scores, pair scores (N choose 2), team-level analysis.

CREATE TABLE role_scores (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id     UUID NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    member_id   UUID NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    role        TEXT NOT NULL CHECK (role IN ('leader','vice','treasurer','mood','idea','mediator','brake')),
    score       DOUBLE PRECISION NOT NULL,
    assigned    BOOLEAN NOT NULL DEFAULT false,
    assigned_unique BOOLEAN,          -- NULL unless assigned; false = duplicate round
    reason      TEXT,                 -- LLM fills later; deterministic pipeline leaves NULL
    UNIQUE (member_id, role)
);

CREATE INDEX idx_role_scores_team ON role_scores (team_id, role, score DESC);

CREATE TABLE pair_scores (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id     UUID NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    member_a_id UUID NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    member_b_id UUID NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    total       INT NOT NULL,
    saju_score  INT NOT NULL,
    mbti_score  INT NOT NULL,
    factors     JSONB NOT NULL,       -- glass-box: every point explained
    reason      TEXT,
    CHECK (member_a_id < member_b_id), -- one canonical row per pair (idempotency)
    UNIQUE (team_id, member_a_id, member_b_id)
);

CREATE INDEX idx_pair_scores_team_total ON pair_scores (team_id, total DESC);

CREATE TABLE team_analysis (
    team_id        UUID PRIMARY KEY REFERENCES teams (id) ON DELETE CASCADE,
    archetype_name TEXT NOT NULL,
    archetype_desc TEXT,
    harmony_score  INT NOT NULL,
    trait_avgs     JSONB NOT NULL,
    element_totals JSONB NOT NULL,
    risk_note      TEXT,
    analyzed_at    TIMESTAMPTZ NOT NULL
);
