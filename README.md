# MiniSearch

MiniSearch is a Java 17 / Spring Boot document search engine with separate authentication, document, and search services. It implements a positional inverted index, Boolean and phrase queries, TF-IDF ranking, prefix/fuzzy matching, autocomplete, and spelling suggestions.

Document uploads commit metadata and an outbox event to PostgreSQL. A background publisher delivers events to Kafka; the search service extracts and tokenizes the stored file, persists the latest index mutation and an outcome event in one transaction, and atomically publishes the index with its owner metadata. Completion feedback marks the matching document version `INDEXED`; terminal failures expose `INDEX_FAILED` with an error. Updates replace a document's searchable version, and deletions leave durable index tombstones before a retryable cleanup worker removes the file and marks it `DELETED`. Startup rebuilds the in-memory index from persisted tokens.

## Quick Start

1. Copy the example environment file:

```shell
cp .env.example .env
```

2. Start the local stack:

```shell
docker compose -f infrastructure/docker/docker-compose.yml up --detach --build
```

3. Check the app:

```shell
curl --fail --silent http://localhost:8081/actuator/health/readiness
curl --fail --silent http://localhost:8082/actuator/health/readiness
curl --fail --silent http://localhost:8083/actuator/health/readiness
```

4. Exercise registration, login, TXT upload, indexed status, phrase search, owner isolation, metadata update, restart recovery, deletion, file cleanup, and a terminal PDF failure:

```shell
python3 scripts/smoke-search.py --restart-search --verify-cleanup --timeout 60
```

The smoke check creates two user accounts, one TXT document, and one intentionally invalid PDF, then deletes both documents. Accounts, event records, cleanup records, and deletion tombstones remain in the development database. Omit `--restart-search` to avoid restarting a container, and omit `--verify-cleanup` to run without Docker CLI access. The script uses Python 3's standard library.

Owners can poll `GET /api/v1/documents/{id}/status` with their bearer token. It returns `documentId`, `version`, `status`, and `indexingError`, including while deletion is pending and after cleanup finishes. Deleted content remains hidden from the document read/list endpoints.

## Common Commands

```shell
# Run unit tests
./mvnw test

# Run the full verification lifecycle
./mvnw verify

# Run integration tests only
./mvnw failsafe:integration-test failsafe:verify

# Build the executable JAR
./mvnw package
```

## Local Development Notes

- [Docker Compose](./infrastructure/docker/docker-compose.yml) starts PostgreSQL, Kafka, topic initialization, and all three services. Search caching defaults to local Caffeine; Redis is optional and is not started by this stack.
- Services listen on ports 8081 (auth), 8082 (documents), and 8083 (search).
- Document and search APIs accept bearer access tokens issued by the auth service. Owner IDs come from the token subject; `X-User-Id` and `X-Owner-Id` do not establish identity.
- The document service writes a shared document volume; the search service mounts it read-only. Kafka delivery is enabled by Compose and can be enabled for host processes with `MINSEARCH_MESSAGING_ENABLED=true`.
- Testcontainers-based integration tests run during the normal Maven test lifecycle when Docker is available.
- See [Docker setup and delivery behavior](./docs/DOCKER.md) for topic names, retries, persistence, and the current deployment limits.

## Documentation

- [Build and Dependency Standards](./docs/BUILD.md)
- [Implementation Task Ledger](./docs/TASKS.md)
- [Specification](./docs/SPECIFICATION.md)
