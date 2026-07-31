-- V8: a team without a name reads as a bug on the waiting screen, which puts
-- the name in the title slot. It is now required at the API boundary, so the
-- column can be too. Existing nameless teams keep working under a placeholder.
UPDATE teams SET name = '이름 없는 팀' WHERE name IS NULL OR btrim(name) = '';
ALTER TABLE teams ALTER COLUMN name SET NOT NULL;
