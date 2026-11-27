-- Read-only capability probe; absence of an installed extension is valid before V15.
SELECT JSONB_BUILD_OBJECT(
    'database', CURRENT_DATABASE(),
    'database_user', CURRENT_USER,
    'encoding', CURRENT_SETTING('server_encoding'),
    'icu_compatible', COALESCE((
        SELECT BOOL_AND(collprovider = 'i' AND collisdeterministic
            AND collversion = '153.136' AND PG_COLLATION_ACTUAL_VERSION(oid) = collversion)
        FROM pg_collation
        WHERE collnamespace = 'pg_catalog'::REGNAMESPACE AND collname = 'und-x-icu'
    ), FALSE),
    'pg_trgm_available', EXISTS (
        SELECT 1 FROM pg_available_extension_versions WHERE name = 'pg_trgm' AND version = '1.6'
    ),
    'pg_trgm_installed', (
        SELECT JSONB_BUILD_OBJECT('version', e.extversion, 'schema', n.nspname)
        FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace WHERE e.extname = 'pg_trgm'
    ),
    'can_create', HAS_DATABASE_PRIVILEGE(CURRENT_USER, CURRENT_DATABASE(), 'CREATE')
) AS capabilities;
