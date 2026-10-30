CREATE TABLE image_uploads (
    id UUID PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES users(id),
    staging_key VARCHAR(512) NOT NULL,
    final_key VARCHAR(512),
    content_type VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_image_uploads_staging_key UNIQUE (staging_key),
    CONSTRAINT uk_image_uploads_final_key UNIQUE (final_key),
    CONSTRAINT ck_image_uploads_staging_key CHECK (staging_key ~ '[^[:space:]]'),
    CONSTRAINT ck_image_uploads_final_key CHECK (final_key IS NULL OR final_key ~ '[^[:space:]]'),
    CONSTRAINT ck_image_uploads_content_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_image_uploads_status CHECK (status IN ('PENDING', 'COMPLETED', 'EXPIRED')),
    CONSTRAINT ck_image_uploads_deadline CHECK (expires_at > created_at),
    CONSTRAINT ck_image_uploads_state_key CHECK (
        (status = 'COMPLETED' AND final_key IS NOT NULL)
        OR (status IN ('PENDING', 'EXPIRED') AND final_key IS NULL)
    )
);

CREATE INDEX idx_image_uploads_owner_user_id ON image_uploads(owner_user_id);
CREATE INDEX idx_image_uploads_status_expires_at ON image_uploads(status, expires_at);
