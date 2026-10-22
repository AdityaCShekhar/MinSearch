# Local Docker Environment

The Compose environment starts the document-search pipeline:

| Service | Container address | Host address | Persistence |
| --- | --- | --- | --- |
| Auth API | `auth-service:8080` | `localhost:8081` | PostgreSQL |
| Document API | `document-service:8080` | `localhost:8082` | PostgreSQL and `documents-data` |
| Search API | `search-service:8080` | `localhost:8083` | PostgreSQL token projection |
| Auth PostgreSQL | `auth-postgres:5432` | `localhost:5432` | `auth-postgres-data` |
| Document PostgreSQL | `document-postgres:5432` | `localhost:5433` | `document-postgres-data` |
| Search PostgreSQL | `search-postgres:5432` | `localhost:5434` | `search-postgres-data` |
| Kafka | `kafka:29092` | `localhost:9092` | `kafka-data` |

Kafka runs as a single combined broker/controller in KRaft mode. The one-shot `kafka-init` service creates `document.uploaded`, `document.updated`, `document.deleted`, `index.completed`, `index.failed`, and each corresponding `.DLT` topic with three partitions each. Compose enables Redis caching; set `MINSEARCH_CACHE_REDIS_ENABLED=false` to use local Caffeine.

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

Each application waits for its own PostgreSQL instance; document and search services also wait for Kafka topic initialization:

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
docker compose -f infrastructure/docker/docker-compose.yml up --detach auth-postgres document-postgres search-postgres kafka-init
```

Host-side application configuration:

```text
Auth:      jdbc:postgresql://localhost:5432/minsearch_auth
Document:  jdbc:postgresql://localhost:5433/minsearch_document
Search:    jdbc:postgresql://localhost:5434/minsearch_search
Kafka:     localhost:9092
```

Each host process has its own `MINSEARCH_DATABASE_URL`, `MINSEARCH_DATABASE_USERNAME`, and `MINSEARCH_DATABASE_PASSWORD`; never point them at the same database. The default local usernames are `minsearch_auth`, `minsearch_document`, and `minsearch_search`, with development passwords `minsearch_auth_dev_password`, `minsearch_document_dev_password`, and `minsearch_search_dev_password`. Application defaults match the Compose defaults. If `.env` changes an `AUTH_POSTGRES_*`, `DOCUMENT_POSTGRES_*`, or `SEARCH_POSTGRES_*` setting, supply the corresponding `MINSEARCH_DATABASE_*` values to that service's host process. Set the same `MINSEARCH_SECURITY_JWT_SECRET` for all services. For document and search processes, set `MINSEARCH_MESSAGING_ENABLED=true`, `MINSEARCH_KAFKA_BOOTSTRAP_SERVERS=localhost:9092`, and the same `MINSEARCH_STORAGE_DOCUMENTS_ROOT`. Give each host process a different `SERVER_PORT` (8081, 8082, and 8083).

## Verify infrastructure

```shell
docker compose -f infrastructure/docker/docker-compose.yml exec auth-postgres pg_isready -U minsearch_auth -d minsearch_auth
docker compose -f infrastructure/docker/docker-compose.yml exec document-postgres pg_isready -U minsearch_document -d minsearch_document
docker compose -f infrastructure/docker/docker-compose.yml exec search-postgres pg_isready -U minsearch_search -d minsearch_search
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
- The shared document volume is writable by document-service and read-only in search-service. Auth, document, and search migrations run only against their independently owned databases. Each instance has its own credentials and durable volume. Search does not query document or auth tables.

## Current limits

This deployment supports one search-service instance. Additional consumers in the same Kafka group would divide updates between independent in-memory indexes. Multi-instance fan-out or shared query serving requires a separate design.

Document updates accept title/language metadata and optional replacement file content. Deletion hides the document immediately from document APIs. Search, autocomplete, and spelling suggestions hide it after the deletion event is consumed and the new index generation is published. Cached responses are keyed by generation, so deletion cannot reuse an earlier generation's cache entry. An outcome that exhausts document-consumer retries needs the dead-letter replay command below before its status or cleanup can proceed. The token projection streams persisted mutations to rebuild the full index at startup; local 10,000/100,000-document recovery and search measurements are available in the [benchmark results](../benchmarks/results/2026-10-19/HTTP-REPORT.md).

The new event payload is schema version 1. Legacy outbox rows containing only document ID/version/storage/checksum require an explicit metadata backfill before publishing; this change does not rewrite old events automatically.

## Moving from the shared database

The three new database volumes start empty. Compose does not copy data from the old `postgres-data` volume, and the old `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_PORT` overrides no longer configure the application databases. Copy the service-specific settings from `.env.example`. Retain the old volume until migration and recovery checks pass; do not run `down --volumes` on an installation containing data you need.

For an existing installation, stop application writers and workers before taking a consistent backup of the shared database, source files, and Kafka state. Split and restore the owned tables and sequences into the corresponding empty database, preserving the service's Flyway history:

| Destination | Owned tables and sequences |
| --- | --- |
| Auth | `users`, `refresh_tokens`, `auth_security_audit`, `flyway_schema_history` |
| Document | `documents`, `document_versions`, `document_outbox`, `processed_events`, `document_cleanup_jobs`, `document_security_audit`, `document_flyway_schema_history` |
| Search | `search_history`, `search_index_documents`, `search_index_revision`, `search_index_content`, `index_outbox`, `search_security_audit`, `search_flyway_schema_history` |

For installations with document migration V4 already applied, also restore `document_visibility` and `document_visibility_revision` into the document database: V5 removes them on startup. Preserve data and sequence values, remove legacy ownership/grants when restoring, and assign each restored object to its destination database user. Keep pending outbox rows, outcome rows, processed-event IDs, and index tombstones intact. Retain the existing source and Kafka volumes so in-flight events remain recoverable. Do not recreate populated tables by running application migrations before restoring their schema and history.

Start the services with the new connections, let Flyway apply pending migrations, and verify login, existing document reads, search recovery, pending indexing, deletion cleanup, and independent readiness before retiring the old database. Backups must now include all three databases plus the source files and recoverable messaging state.

## Redis cache behavior

Compose deploys a password-protected Redis cache for search and autocomplete. Set
`MINSEARCH_CACHE_REDIS_ENABLED=false` to select local Caffeine instead. The search
service uses 200 ms Redis connection/command timeouts and executes queries directly
when Redis is unavailable; cache failure does not affect readiness. Cache entries use
a versioned JSON envelope and hashed, namespaced keys. DTOs are decoded using the
caller's declared type, without enabling polymorphic deserialization. Unknown schema
versions, incompatible types, expired values, and malformed entries are cache misses.
Redis has no published host port and stores no durable application state.

## Replay one dead-letter record

Repair the original cause first (for example, restore missing content or database access).
List the partition end offsets with:

```shell
docker compose -f infrastructure/docker/docker-compose.yml exec -T kafka \
  /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server kafka:29092 --topic document.uploaded.DLT
```

An end offset is the next offset, so the last retained record is at `end - 1`.
Select the actual failed record using broker logs and the original event ID; do not assume
the last record belongs to a particular document. Preview one explicit coordinate:

```shell
docker compose -f infrastructure/docker/docker-compose.yml exec -T search-service \
  java -Dloader.main=com.aditya.minsearch.search.infrastructure.DeadLetterReplay \
  -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  --bootstrap-server kafka:29092 --topic document.uploaded.DLT --partition 2 --offset 4
```

Append `--execute` to publish the selected record. The command supports the five
provisioned MiniSearch `.DLT` topics and validates the schema, topic, outcome type, and
document key. It preserves the original body, event ID, and partition, waits for the
broker acknowledgement, and prints a JSON receipt including the source and target
offsets. Save that receipt with the incident notes. It never joins a consumer group,
commits offsets, deletes the dead letter, or replays a range. Repeating the command is
safe for the version-aware indexing and event-deduplicating outcome consumers.

For a host JVM, replace `/app/app.jar` with
`services/search-service/target/search-service-0.0.1-SNAPSHOT-exec.jar` and use the
host broker address. `--config /path/client.properties` loads Kafka TLS/SASL settings
without exposing credentials as command arguments. A send timeout can mean the broker
accepted the record before the acknowledgement was lost; repeat the same coordinate.

Run the failure/replay rehearsal in an isolated disposable stack:

```shell
python3 -u benchmarks/verify_delivery.py --output benchmarks/output/delivery-check
```

It tests missing-file failure and repair, outcome-transaction failure and repair,
duplicate replay, and an unavailable broker followed by outbox retry. It builds fresh
service images, uses ports 18081–18083, and removes only its own stack and volumes.

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
