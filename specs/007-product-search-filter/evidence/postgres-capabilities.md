# T002 — isolated PostgreSQL capability evidence

Status: PASS for the T002 infrastructure prerequisite only. T001 was checked and baseline.md exists. Deployment target: NOT_VERIFIED. No shared/development database was accessed.

## Identity and isolation

- Container: `91f5c61479013c56cfa768a0db2ecaae0ee511585edc7b7cd233bb9d89e9502f`;name speckit007-t002-20261109.
- Image: `sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b` (pre-existing postgres:17-alpine;--pull=never).
- Observed PostgreSQL17.11,x86_64 Alpine;server/client UTF8;database search_capability;owner postgres;socket connection through docker exec;internal port5432,no host port,network none,no volume mounts,PGDATA tmpfs256MiB.
- Docker daemon29.6.0 required elevated socket access;first sandbox probe failed permission denied,authorized retry succeeded.
- Two separate fault containers used names speckit007-t002-startup-missing and speckit007-t002-startup-privilege;each network none,tmpfs data and read-only task-specific init SQL. All three task containers were removed after collecting results. No image pull/install or application migration occurred.

## Results and reproduction

Full SQL,exit statuses,stdout/stderr,container IDs and fault-entrypoint logs are in [postgres-capability-cases.json](../../../demo/src/test/resources/catalog/search/postgres-capability-cases.json). Eight SQL cases plus two real PostgreSQL-entrypoint startup failures passed their expected outcomes.

Reproduce using a newly named disposable container only:

```sh
docker run -d --pull=never --name <unique-task-container> --network none --tmpfs /var/lib/postgresql/data:rw,noexec,nosuid,size=256m -e POSTGRES_PASSWORD=<disposable-only> -e POSTGRES_DB=search_capability postgres:17-alpine
docker exec -i <unique-task-container> psql -X -v ON_ERROR_STOP=1 -v VERBOSITY=verbose -U postgres -d search_capability
```

Feed cases in recorded order;use -U cap_app for role-specific cases. Missing extension and unprivileged CREATE EXTENSION intentionally exit3. Owner installation succeeds;cap_app has no database CREATE privilege,installation fails SQLSTATE42501,but can use installed functions. similarity('sony','sont')=0.42857143 and % returns true. This is a capability probe,not a calibrated threshold or relevance oracle. SET LOCAL0.42 rolls back to0.3;neither value is approved for production. Vietnamese text roundtrips;normalization equivalence is NOT tested here.

The diagnostic catalog query matching operator function names with LIKE '%trgm%' returned zero rows;it is not an operator inventory proof. Executing the actual % operator successfully under both roles is the operator capability evidence.

For startup cases,write the respective startupCases.sql to an init SQL file,mount read-only as /docker-entrypoint-initdb.d/01-capability.sql in a NEW tmpfs container,wait for its nonzero exit,and compare the recorded error. Missing-extension guard and SET ROLE cap_app;CREATE EXTENSION cause actual PostgreSQL initialization failure. These are infrastructure startup/preflight cases,NOT a claim of Spring Boot SearchCapabilityState/probe503 behavior;that remains T026/T028. No application implementation is needed or performed in T002. Remove only each newly created task container afterwards.

## Flyway/connection integration source inspection

- demo/pom.xml:Boot Flyway starter,flyway-database-postgresql,PostgreSQL JDBC;no new dependency.
- application.properties:DB_URL/DB_USERNAME/DB_PASSWORD externalized;spring.flyway.locations=${FLYWAY_LOCATIONS:classpath:db/migration};JPA ddl-auto=validate/open-in-view=false. Actual runtime credentials/target were not read or used.
- application-test.properties:jdbc:tc:postgresql:17-alpine:///backend_test,ContainerDatabaseDriver,test/test,Hikari max2/min0,Flyway enabled. This shared test URL alone does not prove per-worker isolation.
- Existing migration files are V1–V14;no Flyway lifecycle/history query or migration run is claimed. Future T015/T060 validate actual schema changes;T063 must separately verify deployment capability/privilege/history/taxonomy. Do not infer target capability from this image.
