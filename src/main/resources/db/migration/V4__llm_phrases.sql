-- V4: LLM phrase audit trail + faithfulness eval results, and the team intro slot.

CREATE TABLE llm_generations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id     UUID NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    kind        TEXT NOT NULL CHECK (kind IN ('role_reasons','pair_chemistry','team_intro')),
    grounding   JSONB NOT NULL,       -- exact facts handed to the model (nothing else may appear in output)
    prompt      TEXT NOT NULL,
    output      TEXT,                 -- raw model output; NULL when the call itself failed
    model       TEXT NOT NULL,
    status      TEXT NOT NULL CHECK (status IN ('accepted','rejected','failed')),
    attempt     INT NOT NULL,
    latency_ms  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_llm_generations_team ON llm_generations (team_id, kind, created_at DESC);

CREATE TABLE eval_results (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    generation_id UUID NOT NULL REFERENCES llm_generations (id) ON DELETE CASCADE,
    judge_model   TEXT NOT NULL,
    claims        JSONB NOT NULL,     -- [{subject,text,verdict}] — per-claim supported/unsupported/style
    faithful      BOOLEAN NOT NULL,
    latency_ms    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_eval_results_generation ON eval_results (generation_id);

ALTER TABLE team_analysis ADD COLUMN intro TEXT;
