# Packaged platform verification

Run from the repository root with Java 17 and Docker available:

```shell
./mvnw -Pe2e verify
```

The optional Maven module starts three independent PostgreSQL 17 containers with distinct credentials, plus Kafka 4.1 and Redis 7 containers on an isolated Testcontainers network. It copies freshly packaged auth/document/search JARs into Java 17 runtime containers and tests their real HTTP APIs, migrations, authentication, outbox delivery, indexing, typed Redis cache, PDF subprocess launch, ownership isolation, status feedback, deletion/physical cleanup, audit persistence, restart reconstruction, broker outage/retry, and database/cache readiness behavior. It also checks that domain tables exist only in their owning database and that a document-database outage leaves search, autocomplete, and suggestions available. Production secret checks and deployed JSON logs are enabled. Docker unavailability fails this explicit profile instead of silently skipping it.

Containers and the network are disposed after the suite. Source files use a temporary host directory; no development Compose volume is accessed. Service logs are retained under `integration-tests/target/e2e-logs`. Do not commit generated logs or credentials. The runtime image defaults to `eclipse-temurin:17-jre`; `-Dminsearch.e2e.java-image=...` can select a compatible Java 17 runtime for an offline environment. Results depend on the selected image; record that override with any published evidence.

The normal `./mvnw verify` still runs the smaller algorithm, adapter, security, PostgreSQL/Redis, and shutdown regressions. The complete end-to-end profile is separate because it needs eight application/infrastructure containers (plus the Testcontainers cleanup container) and takes longer.
