-- `ready` was in the CHECK constraint and the enum from the start, but nothing
-- ever wrote or read it. The real state machine is
--   collecting -> processing -> done | failed
-- Keeping the value only made a reader look for a stage that does not exist.
--
-- No data migration is needed: no row can hold 'ready' because no code path
-- could ever set it. The UPDATE below is a belt-and-braces guard so the new
-- constraint cannot fail on a hand-edited row.

UPDATE teams SET status = 'collecting' WHERE status = 'ready';

ALTER TABLE teams DROP CONSTRAINT IF EXISTS teams_status_check;

ALTER TABLE teams
    ADD CONSTRAINT teams_status_check
    CHECK (status IN ('collecting', 'processing', 'done', 'failed'));
