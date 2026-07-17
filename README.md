# MiniSearch

MiniSearch is a Spring Boot document search engine. This repository currently focuses on the local foundation: PostgreSQL migrations, Testcontainers-based repository tests, local filesystem and in-process adapters, RFC 9457 errors, and the build/release scaffolding.

## Quick Start

1. Copy the example environment file:

```shell
cp .env.example .env
```

2. Start the local stack:

```shell
docker compose up --detach --build
```

3. Check the app:

```shell
curl --fail --silent http://localhost:8080/actuator/health/readiness
```

## Common Commands

```shell
# Run unit tests
./mvnw test

# Run the full verification lifecycle
./mvnw verify

# Run formatter and static-analysis checks explicitly
./mvnw spotless:check checkstyle:check

# Apply formatter output
./mvnw spotless:apply

# Run integration tests only
./mvnw failsafe:integration-test failsafe:verify

# Build the executable JAR
./mvnw package
```

## Local Development Notes

- The Docker profile uses PostgreSQL, Redis, Kafka, and the application container defined in [compose.yaml](./compose.yaml).
- The application reads local filesystem roots from `MINSEARCH_STORAGE_DOCUMENTS_ROOT` and `MINSEARCH_STORAGE_INDEX_ROOT`.
- Testcontainers-based integration tests run during the normal Maven test lifecycle when Docker is available.
- The default app configuration listens on `8080` and exposes `health`, `info`, `metrics`, and `prometheus` actuator endpoints.

## Documentation

- [Build and Dependency Standards](./docs/BUILD.md)
- [Implementation Task Ledger](./docs/TASKS.md)
- [Specification](./docs/SPECIFICATION.md)
