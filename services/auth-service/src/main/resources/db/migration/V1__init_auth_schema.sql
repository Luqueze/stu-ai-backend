CREATE TABLE tb_users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE tb_user_ai_credentials (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES tb_users (id) ON DELETE CASCADE,
    provider          VARCHAR(100) NOT NULL,
    encrypted_api_key TEXT         NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_ai_credentials_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_ai_credentials_user_id ON tb_user_ai_credentials (user_id);
