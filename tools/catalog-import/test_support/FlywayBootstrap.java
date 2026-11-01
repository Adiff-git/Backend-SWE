import org.flywaydb.core.Flyway;

/** Test-only bootstrap for an already running disposable PostgreSQL container. */
class FlywayBootstrap {
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("migration directory required");
        String url = System.getenv("IMPORT_TEST_JDBC_URL");
        String user = System.getenv("IMPORT_TEST_DB_USER");
        String password = System.getenv("IMPORT_TEST_DB_PASSWORD");
        if (url == null || user == null || password == null)
            throw new IllegalArgumentException("disposable test connection environment is incomplete");
        Flyway.configure().dataSource(url, user, password)
                .locations("filesystem:" + args[0]).load().migrate();
    }
}
