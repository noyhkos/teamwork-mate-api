-- V6: every member lands on a rung of the ladder. 'member' is the bottom one,
-- replacing the old "duplicate role" idea that assigned_unique flagged.
ALTER TABLE role_scores DROP CONSTRAINT role_scores_role_check;
ALTER TABLE role_scores ADD CONSTRAINT role_scores_role_check
    CHECK (role IN ('leader', 'vice', 'treasurer', 'mood', 'idea', 'mediator', 'brake', 'member'));

ALTER TABLE role_scores DROP COLUMN assigned_unique;
