ALTER TABLE tb_exam_submissions
    DROP CONSTRAINT tb_exam_submissions_exam_id_student_email_key;

CREATE INDEX idx_exam_submissions_exam_id_student_email ON tb_exam_submissions (exam_id, student_email);
