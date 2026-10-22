# Build and Dependency Standards

MiniSearch uses the Maven Wrapper and the Spring Boot parent POM as the authoritative dependency and plugin-management baseline.

## Requirements

- Java 17
- Maven 3.9 or newer in the Maven 3 release line

Use the checked-in wrapper rather than a machine-specific Maven installation:

```shell
./mvnw verify
```

For a shorter local workflow, start with the repository root [README](../README.md), which lists the clean-clone Docker and Maven commands.

## Version-management policy

1. Dependencies managed by Spring Boot must omit an explicit version.
2. Libraries outside Spring Boot's dependency management must use either an imported BOM or a named property in `pom.xml`.
3. Maven plugins not managed by the Spring Boot parent must have a named version property.
4. Snapshot dependencies and dynamic version ranges are not allowed in a release build.
5. Framework version upgrades must be isolated changes with the full test suite and dependency-tree review.

The `verify` lifecycle runs Surefire tests, packages the services, and runs Failsafe integration tests. PostgreSQL repository tests use Testcontainers. Additional formatting, dependency-enforcement, and static-analysis gates remain build-hardening work.

## Common commands

```shell
# Compile and run unit tests
./mvnw test

# Run all verification checks and create the executable JAR
./mvnw verify

# Run integration tests only
./mvnw failsafe:integration-test failsafe:verify

# Inspect the selected dependency graph
./mvnw dependency:tree

# Inspect inherited dependency and plugin management
./mvnw help:effective-pom
```

PostgreSQL repository integration tests use Testcontainers and run as part of the
standard test lifecycle when Docker is available. The dedicated integration-test
phase also exercises the application startup path:

```shell
./mvnw verify
```

For deterministic corpora, isolated HTTP load runs, and JMH allocation profiling, see the [benchmark guide](../benchmarks/README.md). `make bench-api` runs the default HTTP matrix; `make bench-jmh` builds and runs the microbenchmarks. Generated data stays in the ignored `benchmarks/output` directory.

## Current pipeline verification

Run the complete reactor, including shared fixtures, isolated auth persistence, document outcomes/cleanup, search recovery, and the application startup integration check:

```shell
./mvnw verify
```

The shared web/startup tests explicitly isolate database auto-configuration, and the shared repository fixture lives in the shared test resources. Database-backed tests use isolated containers rather than a developer database. Testcontainers tests are skipped when Docker is unavailable; a run with skipped tests does not verify persistence behavior.

For the running services and Kafka delivery, use:

```shell
python3 scripts/smoke-search.py --restart-search --verify-cleanup --timeout 60
```
