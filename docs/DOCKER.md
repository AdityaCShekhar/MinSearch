# Local Docker Environment

The Compose environment starts the document-search pipeline:

| Service | Container address | Host address | Persistence |
| --- | --- | --- | --- |
| Auth API | `auth-service:8080` | `localhost:8081` | PostgreSQL |
| Document API | `document-service:8080` | `localhost:8082` | PostgreSQL and `documents-data` |
| Search API | `search-service:8080` | `localhost:8083` | PostgreSQL token projection |
| PostgreSQL | `postgres:5432` | `localhost:5432` | `postgres-data` |
| Kafka | `kafka:29092` | `localhost:9092` | `kafka-data` |

Kafka runs as a single combined broker/controller in KRaft mode. The one-shot `kafka-init` service creates `document.uploaded`, `document.updated`, `document.deleted`, `index.completed`, `index.failed`, and each corresponding `.DLT` topic with three partitions each. Redis is optional; this stack uses local Caffeine caching.

## Prerequisites

- Docker Engine 20.10.4 or newer
- Docker Compose v2
- At least 4 GiB of memory available to Docker

The checked Compose configuration uses plaintext local network connections and development credentials. Do not expose it to an untrusted network.

## Start the stack

Optionally create a local override file first:

```shell
cp .env.example .env
```

The application services wait for PostgreSQL; document and search services also wait for Kafka topic initialization:

```shell
docker compose -f infrastructure/docker/docker-compose.yml up --detach --build
docker compose -f infrastructure/docker/docker-compose.yml ps
```

The applications listen on ports 8081 (auth), 8082 (documents), and 8083 (search). Check an application with:

```shell
curl --fail http://localhost:8082/actuator/health/readiness
```

Follow application logs with:

```shell
docker compose -f infrastructure/docker/docker-compose.yml logs --follow document-service
```

## Start infrastructure only

To run the applications from an IDE or with Maven, start their infrastructure:

```shell
docker compose -f infrastructure/docker/docker-compose.yml up --detach postgres kafka-init
```

Host-side application configuration:

```text
PostgreSQL: jdbc:postgresql://localhost:5432/minsearch
Kafka:      localhost:9092
```

Set `MINSEARCH_DATABASE_URL`, `MINSEARCH_DATABASE_USERNAME`, and `MINSEARCH_DATABASE_PASSWORD` for all three host processes. Set the same `MINSEARCH_SECURITY_JWT_SECRET` for all services. For document and search processes, set `MINSEARCH_MESSAGING_ENABLED=true`, `MINSEARCH_KAFKA_BOOTSTRAP_SERVERS=localhost:9092`, and the same `MINSEARCH_STORAGE_DOCUMENTS_ROOT`. Give each host process a different `SERVER_PORT` (8081, 8082, and 8083).

## Verify infrastructure

```shell
docker compose -f infrastructure/docker/docker-compose.yml exec postgres pg_isready -U minsearch -d minsearch
docker compose -f infrastructure/docker/docker-compose.yml exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:29092 \
  --list
```

The Kafka topic list must contain:

```text
document.deleted
document.deleted.DLT
document.updated
document.updated.DLT
document.uploaded
document.uploaded.DLT
index.completed
index.completed.DLT
index.failed
index.failed.DLT
```

## Verify the application flow

```shell
python3 scripts/smoke-search.py --restart-search --verify-cleanup --timeout 60
```

This creates two accounts, logs in, uploads a TXT file containing a unique phrase, waits for searchable content and `INDEXED` status, verifies owner isolation, changes searchable metadata, restarts search to check durable recovery, and deletes the document. It waits for `DELETED`, checks the source file is absent inside the document container, and exercises an invalid PDF's terminal failure and subsequent deletion. Accounts, event/cleanup records, and deletion tombstones remain in the local database.

## Delivery and persistence

- Uploads return `202` after committing document metadata and an outbox row. The poller runs every second by default and marks an event published only after Kafka acknowledges it.
- Events are keyed by document ID. Uploads, updates, and deletions use separate topics, so their relative arrival order is not guaranteed; persisted document versions reject duplicate and older changes. Deletion increments the version and keeps a tombstone.
- A failed publisher send leaves the outbox row pending for another poll. A consumer failure is retried twice with a one-second delay, then published to the source topic's `.DLT`. Failed dead-letter publication is not treated as success. This follows [Spring Kafka's error-handler behavior](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html).
- The search service verifies the source checksum, stores tokenized content and owner metadata in `search_index_documents`, and publishes both together after the database commit. It reconstructs the in-memory index at startup without rereading source files. A persistent sequence supplies cache generation IDs across restarts.
- Each index mutation and its `index.completed` outbox row commit atomically. The outcome relay waits until the required generation is visible in this search process, then sends with broker acknowledgement. Pending outcomes survive a restart. Migration V5 backfills outcomes for existing durable projections.
- After consumer retries are exhausted, a successfully dead-lettered document event also records a durable `index.failed` outcome when its identity is valid. Older failures cannot override an already indexed version. Malformed messages without a trustworthy document identity go to the dead-letter topic without changing document status.
- Document-service deduplicates outcome event IDs and applies only the current document version. A successful outcome marks `INDEXED` and clears its error; a terminal failure marks `INDEX_FAILED` with a bounded error description. `GET /api/v1/documents/{id}/status` remains available to the owner after logical deletion.
- A matching index deletion acknowledgement creates a durable cleanup job. Cleanup locks and checks the document version, deletion state, and storage key before deleting. Storage failures retry with capped exponential delays (up to 60 seconds); the document remains `DELETE_PENDING` until file deletion succeeds and `DELETED` commits. Repeating an already completed deletion is safe.
- The shared document volume is writable by document-service and read-only in search-service. Auth, document, and search migrations use separate history tables in the shared database.

## Current limits

This deployment supports one search-service instance. Additional consumers in the same Kafka group would divide updates between independent in-memory indexes. Multi-instance fan-out or shared query serving requires a separate design.

Document updates currently change title/language metadata; replacing file content is not exposed by the API. Deletion disappears from search after its event is consumed, rather than immediately at the document transaction commit. Dead-letter replay is manual: an outcome that exhausts document-consumer retries needs replay before its status or cleanup can proceed. The token projection rebuilds the full index at startup, so large-corpus recovery still needs measurement.

The new event payload is schema version 1. Legacy outbox rows containing only document ID/version/storage/checksum require an explicit metadata backfill before publishing; this change does not rewrite old events automatically.

## Stop or reset

Stop containers while retaining data:

```shell
docker compose -f infrastructure/docker/docker-compose.yml down
```

Delete all local database, broker, and source-document data:

```shell
docker compose -f infrastructure/docker/docker-compose.yml down --volumes
```

The reset command is destructive and should only be used for disposable local data.

## Configuration

Supported `.env` overrides are documented in `.env.example`. Defaults allow startup without creating `.env`. Compose passes container-network addresses to the application; use host addresses only when the application runs outside Compose.

The default Kafka cluster uses plaintext listeners and a replication factor of one. A production deployment must use separate brokers/controllers, authentication and TLS, replication, secrets management, resource limits, and externally managed durable storage.
