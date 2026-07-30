-- V1: input-side tables (teams / members / saju_facts)
-- Score tables (role_scores / pair_scores / team_analysis) arrive with the analysis pipeline.

CREATE TABLE teams (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT,
    invite_token    TEXT NOT NULL UNIQUE,
    admin_token     TEXT NOT NULL UNIQUE,
    share_slug      TEXT UNIQUE,
    status          TEXT NOT NULL DEFAULT 'collecting'
                    CHECK (status IN ('collecting', 'ready', 'processing', 'done', 'failed')),
    settings        JSONB NOT NULL DEFAULT '{}'::jsonb,
    card_image_url  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ
);

CREATE TABLE members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id     UUID NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    nickname    TEXT NOT NULL,
    birth_date  DATE NOT NULL,
    birth_time  TIME,            -- NULL means birth time unknown (hour pillar must not be fabricated)
    gender      TEXT NOT NULL CHECK (gender IN ('M', 'F')),
    mbti        CHAR(4) NOT NULL CHECK (mbti ~ '^[EI][SN][TF][JP]$'),
    calendar    TEXT NOT NULL DEFAULT 'solar' CHECK (calendar IN ('solar', 'lunar')),
    leap_month  BOOLEAN NOT NULL DEFAULT false,
    entered_by  TEXT NOT NULL CHECK (entered_by IN ('self', 'admin')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (team_id, nickname)
);

CREATE INDEX idx_members_team ON members (team_id);

CREATE TABLE saju_facts (
    member_id       UUID PRIMARY KEY REFERENCES members (id) ON DELETE CASCADE,
    birth_time_known BOOLEAN NOT NULL,
    day_master      TEXT NOT NULL,
    pillars         JSONB NOT NULL,
    five_elements   JSONB NOT NULL,
    -- flattened counts for cheap GROUP BY aggregation across a team
    wood_cnt        INT NOT NULL,
    fire_cnt        INT NOT NULL,
    earth_cnt       INT NOT NULL,
    metal_cnt       INT NOT NULL,
    water_cnt       INT NOT NULL,
    ten_gods        JSONB NOT NULL,
    day_strength    JSONB,          -- NULL when birth time unknown
    geukguk         TEXT,           -- NULL when birth time unknown
    compact_text    TEXT NOT NULL,  -- LLM grounding source
    full_compact_text TEXT,
    calc_version    TEXT NOT NULL,
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
