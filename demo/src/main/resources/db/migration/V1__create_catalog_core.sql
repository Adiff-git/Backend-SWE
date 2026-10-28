CREATE TABLE categories (
    id UUID CONSTRAINT pk_categories PRIMARY KEY,
    name TEXT NOT NULL,
    parent_category_id UUID,
    CONSTRAINT fk_categories_parent_category_id FOREIGN KEY (parent_category_id) REFERENCES categories (id),
    CONSTRAINT ck_categories_name_nonblank CHECK (name ~ '[^[:space:]]')
);

CREATE TABLE products (
    id UUID CONSTRAINT pk_products PRIMARY KEY,
    category_id UUID NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    status VARCHAR NOT NULL,
    CONSTRAINT fk_products_category_id FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_products_name_nonblank CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE product_variants (
    id UUID CONSTRAINT pk_product_variants PRIMARY KEY,
    product_id UUID NOT NULL,
    sku TEXT NOT NULL,
    option_combination JSONB NOT NULL,
    selling_price NUMERIC NOT NULL,
    CONSTRAINT fk_product_variants_product_id FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_product_variants_sku_nonblank CHECK (sku ~ '[^[:space:]]'),
    CONSTRAINT ck_product_variants_options_object CHECK (jsonb_typeof(option_combination) = 'object'),
    CONSTRAINT ck_product_variants_price_whole_finite CHECK (
        selling_price::TEXT NOT IN ('NaN', 'Infinity', '-Infinity')
        AND selling_price = TRUNC(selling_price)
    ),
    CONSTRAINT uk_product_variants_product_id_option_combination UNIQUE (product_id, option_combination)
);

CREATE UNIQUE INDEX uk_product_variants_sku_ci ON product_variants (lower(sku));
CREATE INDEX idx_products_status_id ON products (status, id);
CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_product_variants_product_id ON product_variants (product_id);
