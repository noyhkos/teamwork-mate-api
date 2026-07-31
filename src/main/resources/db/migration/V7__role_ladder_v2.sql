-- V7: 부리더 was a weaker copy of 리더 by construction (0.7 × leader), so it
-- read as a consolation prize. It becomes 책략가, and four roles join the
-- ladder so fewer people fall through to 일반 멤버.
ALTER TABLE role_scores DROP CONSTRAINT role_scores_role_check;

UPDATE role_scores SET role = 'strategist' WHERE role = 'vice';

ALTER TABLE role_scores ADD CONSTRAINT role_scores_role_check
    CHECK (role IN ('leader', 'strategist', 'treasurer', 'fixer', 'enforcer', 'idea',
                    'diplomat', 'mediator', 'mood', 'scribe', 'brake', 'member'));
