-- Reporting moderation backfill for existing environments.
ALTER TABLE IF EXISTS questions
    ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN NOT NULL DEFAULT FALSE;

-- Essay / mixed exam support backfill.
ALTER TABLE IF EXISTS questions
    ADD COLUMN IF NOT EXISTS question_type VARCHAR(30) NOT NULL DEFAULT 'MULTIPLE_CHOICE';

ALTER TABLE IF EXISTS questions
    ADD COLUMN IF NOT EXISTS sample_answer TEXT;

ALTER TABLE IF EXISTS questions
    ADD COLUMN IF NOT EXISTS grading_guide TEXT;

ALTER TABLE IF EXISTS exam_attempt_answers
    ADD COLUMN IF NOT EXISTS text_answer TEXT;

ALTER TABLE IF EXISTS exam_attempt_answers
    ADD COLUMN IF NOT EXISTS teacher_feedback TEXT;

ALTER TABLE IF EXISTS exam_attempt_answers
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'AUTO_GRADED';

ALTER TABLE IF EXISTS exam_attempt_answers
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

ALTER TABLE IF EXISTS exam_attempts
    DROP CONSTRAINT IF EXISTS exam_attempts_status_check;

ALTER TABLE IF EXISTS exam_attempts
    ADD CONSTRAINT exam_attempts_status_check
    CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'AUTO_SUBMITTED', 'PARTIALLY_GRADED', 'GRADED'));
