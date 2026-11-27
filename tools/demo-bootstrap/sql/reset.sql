-- Read-only reset preconditions. Actual DROP/CREATE use psycopg Identifier and the
-- fixed backend_demo constant in database.reset_database, never substituted text.
SELECT datname,pg_get_userbyid(datdba) AS owner FROM pg_database WHERE datname='backend_demo';
SELECT pid FROM pg_stat_activity WHERE datname='backend_demo';
