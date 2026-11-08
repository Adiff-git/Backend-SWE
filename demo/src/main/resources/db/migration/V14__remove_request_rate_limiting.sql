-- Preserve applied migration checksums and existing business state.
ALTER TABLE verification_mail_outbox DROP COLUMN rate_reservation_id;
ALTER TABLE oauth_login_attempts DROP COLUMN rate_reservation_id;
DROP TABLE rate_limit_events;
DROP TABLE rate_limit_operations;
DROP TABLE rate_limit_reservations;
DROP TABLE rate_limit_buckets;
ALTER TABLE email_verification_challenges DROP CONSTRAINT ck_email_verification_challenges_active;
ALTER TABLE email_verification_challenges DROP COLUMN failed_attempts;
ALTER TABLE email_verification_challenges ADD CONSTRAINT ck_email_verification_challenges_active
    CHECK (NOT active OR (code_mac IS NOT NULL AND consumed_at IS NULL AND superseded_at IS NULL));
