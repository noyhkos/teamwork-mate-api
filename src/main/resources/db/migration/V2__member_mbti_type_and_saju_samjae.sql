-- V2:
-- 1) members.mbti CHAR(4) -> TEXT: Hibernate validates String as varchar-family;
--    the format is already enforced by the regex CHECK constraint, so CHAR added nothing.
-- 2) saju_facts.samjae: folk 3-year cycle status computed by calc (V1 predates it).

ALTER TABLE members ALTER COLUMN mbti TYPE TEXT;

ALTER TABLE saju_facts ADD COLUMN samjae JSONB;
