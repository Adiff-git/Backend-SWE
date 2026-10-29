CREATE TABLE brands (
    id UUID CONSTRAINT pk_brands PRIMARY KEY,
    code TEXT NOT NULL CONSTRAINT uk_brands_code UNIQUE,
    name TEXT NOT NULL,
    CONSTRAINT ck_brands_code CHECK (code ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_brands_name CHECK (name ~ '[^[:space:]]')
);
ALTER TABLE products ADD COLUMN brand_id UUID REFERENCES brands(id);
CREATE INDEX idx_products_brand_id ON products(brand_id);
ALTER TABLE product_variants ADD COLUMN technical_specifications JSONB NOT NULL
    DEFAULT '{"schema_version":1,"values":{}}'::JSONB;
ALTER TABLE product_variants ADD CONSTRAINT ck_product_variants_technical_specifications CHECK (
    jsonb_typeof(technical_specifications) = 'object'
    AND technical_specifications ? 'schema_version'
    AND technical_specifications ? 'values'
    AND technical_specifications->'schema_version' = '1'::JSONB
    AND jsonb_typeof(technical_specifications->'values') = 'object'
    AND technical_specifications - 'schema_version' - 'values' = '{}'::JSONB
);
CREATE TABLE catalog_source_links (
    id UUID CONSTRAINT pk_catalog_source_links PRIMARY KEY,
    source TEXT NOT NULL,
    source_key TEXT NOT NULL,
    source_url TEXT NOT NULL,
    source_record_id TEXT,
    product_variant_id UUID NOT NULL REFERENCES product_variants(id),
    source_payload_sha256 TEXT NOT NULL,
    normalized_content_sha256 TEXT NOT NULL,
    normalization_version TEXT NOT NULL,
    approved_manifest_sha256 TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_catalog_source_links_source_source_key UNIQUE(source, source_key),
    CONSTRAINT ck_catalog_source_links_identity CHECK (
        source ~ '[^[:space:]]' AND source_key ~ '[^[:space:]]'
        AND source_url ~ '^https?://' AND normalization_version ~ '[^[:space:]]'),
    CONSTRAINT ck_catalog_source_links_hashes CHECK (
        source_payload_sha256 ~ '^[0-9a-f]{64}$'
        AND normalized_content_sha256 ~ '^[0-9a-f]{64}$'
        AND approved_manifest_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_catalog_source_links_product_variant_id ON catalog_source_links(product_variant_id);
