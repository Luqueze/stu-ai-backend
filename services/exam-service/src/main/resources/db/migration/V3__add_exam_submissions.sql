CREATE TABLE tb_exam_submissions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id            UUID NOT NULL REFERENCES tb_exams (id) ON DELETE CASCADE,
    student_email      VARCHAR(255)   NOT NULL,
    selected_options   INT[]          NOT NULL,
    correct_count      INT            NOT NULL,
    total_questions    INT            NOT NULL,
    score_percentage   DOUBLE PRECISION NOT NULL,
    submitted_at       TIMESTAMP      NOT NULL DEFAULT now(),
    UNIQUE (exam_id, student_email)
);

CREATE INDEX idx_exam_submissions_exam_id ON tb_exam_submissions (exam_id);
