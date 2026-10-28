CREATE TABLE product_images (
    id UUID CONSTRAINT pk_product_images PRIMARY KEY,
    product_id UUID NOT NULL,
    object_key TEXT NOT NULL,
    alt_text TEXT NOT NULL,
    content_type VARCHAR NOT NULL,
    byte_size BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    display_order INTEGER NOT NULL,
    is_primary BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_product_images_product_id FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_product_images_object_key_nonblank CHECK (object_key ~ '[^[:space:]]'),
    CONSTRAINT ck_product_images_alt_text_nonblank CHECK (alt_text ~ '[^[:space:]]'),
    CONSTRAINT ck_product_images_content_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_product_images_byte_size CHECK (byte_size BETWEEN 1 AND 10485760),
    CONSTRAINT ck_product_images_dimensions CHECK (width > 0 AND height > 0),
    CONSTRAINT ck_product_images_display_order CHECK (display_order >= 0),
    CONSTRAINT uk_product_images_object_key UNIQUE (object_key),
    CONSTRAINT uk_product_images_product_id_display_order UNIQUE (product_id, display_order),
    CONSTRAINT uk_product_images_product_id_id UNIQUE (product_id, id)
);

CREATE UNIQUE INDEX uk_product_images_product_id_primary ON product_images (product_id) WHERE is_primary;
CREATE INDEX idx_product_images_product_id ON product_images (product_id);

CREATE TABLE product_image_uploads (
    id UUID CONSTRAINT pk_product_image_uploads PRIMARY KEY,
    product_id UUID NOT NULL,
    preparer_subject TEXT NOT NULL,
    staging_key TEXT NOT NULL,
    durable_key TEXT,
    claim_token UUID,
    claim_expires_at TIMESTAMPTZ,
    proposed_content_type VARCHAR NOT NULL,
    proposed_byte_size BIGINT NOT NULL,
    alt_text TEXT NOT NULL,
    state VARCHAR NOT NULL,
    prepared_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    product_image_id UUID,
    CONSTRAINT fk_product_image_uploads_product_id FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_image_uploads_product_image_id FOREIGN KEY (product_id, product_image_id)
        REFERENCES product_images (product_id, id),
    CONSTRAINT ck_product_image_uploads_preparer_nonblank CHECK (preparer_subject ~ '[^[:space:]]'),
    CONSTRAINT ck_product_image_uploads_staging_key_nonblank CHECK (staging_key ~ '[^[:space:]]'),
    CONSTRAINT ck_product_image_uploads_durable_key_nonblank CHECK (durable_key IS NULL OR durable_key ~ '[^[:space:]]'),
    CONSTRAINT ck_product_image_uploads_alt_text_nonblank CHECK (alt_text ~ '[^[:space:]]'),
    CONSTRAINT ck_product_image_uploads_content_type CHECK (proposed_content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_product_image_uploads_byte_size CHECK (proposed_byte_size BETWEEN 1 AND 10485760),
    CONSTRAINT ck_product_image_uploads_state CHECK (state IN ('PREPARED', 'VERIFIED', 'PUBLISHED', 'EXPIRED', 'FAILED')),
    CONSTRAINT ck_product_image_uploads_expiry CHECK (expires_at > prepared_at),
    CONSTRAINT ck_product_image_uploads_claim_pair CHECK ((claim_token IS NULL) = (claim_expires_at IS NULL)),
    CONSTRAINT ck_product_image_uploads_published CHECK (
        state <> 'PUBLISHED'
        OR (product_image_id IS NOT NULL AND published_at IS NOT NULL AND published_at < expires_at)
    ),
    CONSTRAINT uk_product_image_uploads_staging_key UNIQUE (staging_key),
    CONSTRAINT uk_product_image_uploads_durable_key UNIQUE (durable_key),
    CONSTRAINT uk_product_image_uploads_product_image_id UNIQUE (product_image_id)
);

CREATE INDEX idx_product_image_uploads_product_id ON product_image_uploads (product_id);
CREATE INDEX idx_product_image_uploads_state_expires_at ON product_image_uploads (state, expires_at);
CREATE INDEX idx_product_image_uploads_state_claim_expires_at ON product_image_uploads (state, claim_expires_at);
