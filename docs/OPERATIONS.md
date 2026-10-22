# Operational checks

All services expose public `/actuator/health/liveness` and `/actuator/health/readiness`. Liveness depends only on process state. Readiness includes only the service's own PostgreSQL instance; document readiness also verifies writable source storage, and search readiness requires successful reconstruction of a complete durable index. Redis is optional and excluded from readiness. Kafka outages leave existing searches available while outbox work is retained for retry; monitor queue age and listener lag separately. Do not restart healthy search processes solely because Kafka or Redis is down.

`/actuator/prometheus` and `/actuator/metrics` require an ADMIN bearer token. Provision an administrator explicitly in the auth database, obtain a normal short-lived access token, and provide it through the scraper's bearer-token file mechanism; arrange token renewal before expiry. Keep metrics inside the service network. No permanent monitoring bypass or built-in administrator credential is supplied.

The Prometheus registry publishes JVM/process/GC/connection-pool metrics and these application measurements:

| Measurement | Meaning |
| --- | --- |
| `minsearch.http.request` / `.count` | Duration/count by matched route template, finite method, and status; includes security rejection paths |
| `minsearch.auth.requests`, `.refresh.reuse` | Auth success/rejection and detected reuse |
| `minsearch.security.rate.rejected` | Requests rejected by rate guards |
| `minsearch.search.duration`, `.results` | Query latency/outcome/type and result-count distribution |
| `minsearch.search.cache.hits`, `.misses` | Actual search cache lookups |
| `minsearch.cache.operations` | Redis read/write/eviction success, misses, and errors |
| `cache.*` with cache `minsearch-local` | Caffeine hit/miss/eviction/size measurements |
| `minsearch.index.duration`, `.events`, `.event.age.seconds` | Event handling latency/outcome and source-event age; duplicate delivery is included in event handling counts |
| `minsearch.index.retries`, `.deadlettered` | Consumer retries and successfully recovered dead letters |
| `minsearch.index.documents`, `.terms`, `.postings`, `.positions`, `.generation` | Published-generation gauges |
| `minsearch.index.payload.estimated.bytes` | Term/posting/position estimate; excludes map/object/generation overhead; use JVM heap and profiling for actual memory |
| `minsearch.upload.duration`, `.bytes` | Server-side upload work after multipart parsing and accepted content bytes |
| `minsearch.documents.status`, `.bytes` | Persisted status counts and live source bytes |
| `minsearch.outbox.pending`, `.oldest.seconds`, `.retry.attempts` | Durable backlog, oldest unpublished event, and retained retry attempts by fixed queue name |
| `minsearch.cleanup.pending` | Pending physical-deletion jobs |

Micrometer also supplies Spring Kafka producer/consumer metrics, including assigned-partition lag while consumers run. Database gauges return NaN when the database cannot be queried; never interpret that as an empty backlog. Estimated index gauges scan the current immutable generation at scrape time, so choose a moderate scrape interval for large indexes. Generation is a gauge, not a label. User IDs, query text, filenames, document IDs, and raw URLs are never metric labels.

Compose enables ECS JSON console logs. Correlation IDs contain at most 64 safe ASCII characters; invalid inbound values are replaced. Application error logs avoid request bodies and exception messages that might contain content or credentials. Consult [security operation](SECURITY.md) for secret rotation and audit tables.

Alert on sustained readiness failure, rising outbox age, dead letters, terminal indexing failures, cache errors, and heap pressure. Exact thresholds depend on the deployment and ingestion workload; establish them using the retained benchmark methodology rather than treating the synthetic corpus as production capacity.

## Shutdown and restart

Use SIGTERM or `docker compose stop`, allowing the configured 90-second Compose grace period. HTTP uses graceful shutdown. Context closing immediately closes worker admission; Kafka stops before the worker-drain phase, and pollers stop between durable items. Active indexing retains a lease until the prepared generation is durably saved and atomically published. The shared coordinator waits up to 25 seconds, with 30 seconds per Spring lifecycle phase; an abandoned process leaves unacknowledged Kafka records and unpublished outbox rows for replay. Never delete durable rows to resolve a shutdown delay.

Analytics uses two managed threads and a bounded 1000-item queue, rejects excess work without failing searches, and drains up to 25 seconds before database destruction. Dropped storage/capacity work is counted. Analytics remains best effort.

On restart, search reconstructs tokens, ownership metadata, revision, and deletion tombstones from PostgreSQL before readiness. It does not require original files to reconstruct already persisted tokens. Recovery publishes one complete snapshot; a failed cursor never exposes an incomplete generation. Outbox outcomes are published only when their required generation is visible. The optional binary snapshot utility has checksum rejection tests; the deployed recovery source is the PostgreSQL token projection. Preserve all three service databases and the source volume together for recovery, including files not yet indexed, and retain recoverable Kafka state. Database names, credentials, and volumes are service-specific; see [database migration and backup boundaries](DOCKER.md#moving-from-the-shared-database).
