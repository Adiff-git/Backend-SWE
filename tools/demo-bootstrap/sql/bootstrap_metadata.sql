-- The sole approved importer-owned operational table. No application migration is changed.
CREATE TABLE bootstrap_import_runs (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    metadata_version INTEGER NOT NULL CHECK (metadata_version = 1),
    attempt_id UUID NOT NULL,
    dataset_id TEXT NOT NULL CHECK (LENGTH(BTRIM(dataset_id)) > 0),
    dataset_version TEXT NOT NULL CHECK (LENGTH(BTRIM(dataset_version)) > 0),
    artifact_sha256 TEXT NOT NULL CHECK (artifact_sha256 ~ '^[0-9a-f]{64}$'),
    source_revision TEXT NOT NULL CHECK (source_revision ~ '^[0-9a-f]{40}$'),
    importer_version TEXT NOT NULL CHECK (LENGTH(BTRIM(importer_version)) > 0),
    importer_digest TEXT NOT NULL CHECK (importer_digest ~ '^[0-9a-f]{64}$'),
    migration_fingerprint JSONB NOT NULL CHECK (JSONB_TYPEOF(migration_fingerprint) = 'object'),
    anchor_at TIMESTAMPTZ NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('PREPARING', 'IMPORTING', 'COMPLETED', 'FAILED')),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    resource_identity JSONB NOT NULL CHECK (JSONB_TYPEOF(resource_identity) = 'object'),
    verification_summary JSONB NOT NULL DEFAULT '{}'::JSONB CHECK (JSONB_TYPEOF(verification_summary) = 'object'),
    error_code TEXT CHECK (error_code IS NULL OR error_code ~ '^[A-Z0-9_]{1,64}$'),
    CONSTRAINT ck_bootstrap_import_runs_completion CHECK (
        (state = 'COMPLETED') = (completed_at IS NOT NULL)
        AND (completed_at IS NULL OR completed_at >= started_at)
    )
);
