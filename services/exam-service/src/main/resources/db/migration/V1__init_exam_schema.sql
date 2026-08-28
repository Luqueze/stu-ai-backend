CREATE TABLE tb_exams (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    theme             VARCHAR(255) NOT NULL,
    question_count    INT          NOT NULL,
    difficulty        VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    failure_reason    VARCHAR(30),
    failure_message   TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE tb_exam_questions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id                UUID NOT NULL REFERENCES tb_exams (id) ON DELETE CASCADE,
    statement              TEXT NOT NULL,
    options                TEXT[] NOT NULL,
    correct_option_index   INT  NOT NULL,
    created_at             TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_exam_questions_exam_id ON tb_exam_questions (exam_id);
