# Local Docker Environment

The Compose environment provides every service required by the MiniSearch architecture:

| Service | Container address | Host address | Persistence |
| --- | --- | --- | --- |
| MiniSearch API | `app:8080` | `localhost:8080` | document and index volumes |
| PostgreSQL | `postgres:5432` | `localhost:5432` | `postgres-data` |
| Redis | `redis:6379` | `localhost:6379` | `redis-data` |
| Kafka | `kafka:29092` | `localhost:9092` | `kafka-data` |

Kafka runs as a single combined broker/controller in KRaft mode. The one-shot `kafka-init` service creates `document.uploaded`, `document.updated`, `document.deleted`, and `index.completed` with three partitions each. This topology is for local development, not a production high-availability deployment.

## Prerequisites

- Docker Engine 20.10.4 or newer
- Docker Compose v2
- At least 4 GiB of memory available to Docker

The checked Compose configuration uses plaintext local network connections and development credentials. Do not expose it to an untrusted network.

## Start everything

Optionally create a local override file first:

```shell
cp .env.example .env
```

Then build and start the complete environment:

```shell
docker compose up --detach --build
docker compose ps
```

The API is ready when `app` is healthy:

```shell
curl --fail http://localhost:8080/actuator/health/readiness
```

Follow application logs with:

```shell
docker compose logs --follow app
```

## Start infrastructure only

Use this mode when running the application from an IDE or with Maven:

```shell
docker compose up --detach postgres redis kafka kafka-init
```

Host-side application configuration:

```text
PostgreSQL: jdbc:postgresql://localhost:5432/minsearch
Redis:      localhost:6379
Kafka:      localhost:9092
```

## Verify infrastructure

```shell
docker compose exec postgres pg_isready -U minsearch -d minsearch
docker compose exec redis redis-cli ping
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:29092 \
  --list
```

The Kafka topic list must contain:

```text
document.deleted
document.updated
document.uploaded
index.completed
```

## Stop or reset

Stop containers while retaining data:

```shell
docker compose down
```

Delete all local database, cache, broker, document, and index data:

```shell
docker compose down --volumes
```

The reset command is destructive and should only be used for disposable local data.

## Configuration

Supported `.env` overrides are documented in `.env.example`. Defaults allow startup without creating `.env`. Compose passes container-network addresses to the application; use host addresses only when the application runs outside Compose.

The default Kafka cluster uses plaintext listeners and a replication factor of one. A production deployment must use separate brokers/controllers, authentication and TLS, replication, secrets management, resource limits, and externally managed durable storage.
