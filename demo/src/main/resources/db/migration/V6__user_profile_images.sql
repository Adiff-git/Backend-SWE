ALTER TABLE users ADD COLUMN profile_image_id BIGINT REFERENCES image_uploads(id);
CREATE INDEX idx_users_profile_image_id ON users(profile_image_id);
