ALTER TABLE tb_exam_submissions
    ADD COLUMN flagged_questions BOOLEAN[] NOT NULL DEFAULT '{}';
