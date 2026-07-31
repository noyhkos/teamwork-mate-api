-- V5: collapse the two capability tokens into one.
--
-- The admin token gated three things: running the analysis, proxy member entry,
-- and reading everyone's birth data. The first is idempotent so it never needed
-- gating, the second was already possible with the invite token, and the third
-- is better served by showing that data to nobody. What remained was a second
-- link the host could lose with no way to recover the team.

ALTER TABLE teams DROP COLUMN admin_token;
ALTER TABLE teams RENAME COLUMN invite_token TO access_token;

-- entered_by distinguished the two entry endpoints, which are now one.
ALTER TABLE members DROP COLUMN entered_by;
