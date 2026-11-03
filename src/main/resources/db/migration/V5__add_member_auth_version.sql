ALTER TABLE kenshu_member
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_kenshu_member_auth_version CHECK (auth_version >= 0);
COMMENT ON COLUMN kenshu_member.auth_version IS '認証世代番号';
