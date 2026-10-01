DROP TABLE tb_password_reset_tokens;

ALTER TABLE tb_users ADD COLUMN keycloak_id UUID UNIQUE;
ALTER TABLE tb_users DROP COLUMN password_hash;
ALTER TABLE tb_users DROP COLUMN role;
