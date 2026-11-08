-- Existing product galleries and legacy upload sessions remain intact.
ALTER TABLE product_images ADD COLUMN source_image_id UUID REFERENCES image_uploads(id);
ALTER TABLE product_images ADD CONSTRAINT uk_product_images_source UNIQUE (product_id, source_image_id);
