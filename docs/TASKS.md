# MiniSearch Implementation Task Ledger

This file is the source of truth for implementation progress. Tasks are completed in listed order unless a documented dependency permits parallel work.

## Status legend

- `[x]` Done and verified
- `[>]` In progress; there must be at most one active task
- `[ ]` Pending
- `[!]` Blocked; the reason must be recorded under the task

## Version 1 progress

Deferred stretch goals are excluded from these counts.

| State | Count |
| --- | ---: |
| Done | 3 |
| In progress | 0 |
| Pending | 71 |
| Blocked | 1 |

## Phase 0: Specification

- [x] **SPEC-001** Capture product scope and functional requirements.
- [x] **SPEC-002** Define architecture, contracts, data model, quality standards, milestones, and acceptance gates.

Exit gate: [Specification](./SPECIFICATION.md) is implementation-ready and internally reviewed. **Passed.**

## Phase 1: Foundation

- [!] **FOUND-000** Provision the complete local Docker environment and verify healthy startup.
  - Setup complete: application image, PostgreSQL, Redis, Kafka KRaft broker, topic initialization, volumes, health checks, environment template, and operating guide.
  - Verified: Compose model, Maven tests/package, and Docker-profile readiness endpoint.
  - Blocker: the local Docker daemon is not running, so full container startup and topic creation cannot yet be exercised.
- [x] **FOUND-001** Correct Maven project metadata and establish dependency/version management.
- [ ] **FOUND-002** Create package-by-feature module boundaries: `auth`, `document`, `index`, `search`, `autocomplete`, `cache`, `analytics`, and `shared`.
- [ ] **FOUND-003** Add architecture tests that enforce module boundaries.
- [ ] **FOUND-004** Define typed, validated application configuration and local/test profiles.
- [ ] **FOUND-005** Add PostgreSQL, migrations, and repository test infrastructure.
- [ ] **FOUND-006** Add local filesystem storage and in-process event/cache adapters behind ports.
- [ ] **FOUND-007** Add the RFC 9457 error model, validation handling, and stable error codes.
- [ ] **FOUND-008** Add correlation IDs, structured logging, health endpoints, and baseline metrics.
- [ ] **FOUND-009** Add formatting, static analysis, unit-test, and integration-test build checks.
- [ ] **FOUND-010** Document reproducible local development and test commands.

Exit gate: application starts from a clean environment, migrations pass, build checks pass, and architecture tests enforce boundaries.

## Phase 2: Authentication and authorization

- [ ] **AUTH-001** Create user and refresh-token migrations and persistence adapters.
- [ ] **AUTH-002** Implement registration with normalized unique email and adaptive password hashing.
- [ ] **AUTH-003** Implement login and short-lived signed access JWTs.
- [ ] **AUTH-004** Implement refresh-token families, hashing, atomic rotation, and reuse detection.
- [ ] **AUTH-005** Implement logout and refresh-token revocation.
- [ ] **AUTH-006** Configure request authentication, role policy, and public endpoint allowlist.
- [ ] **AUTH-007** Add authentication rate limiting and generic credential errors.
- [ ] **AUTH-008** Add unit, integration, security, and end-to-end authentication tests.

Exit gate: requirements AUTH-01 through AUTH-07 pass automated tests.

## Phase 3: Document lifecycle

- [ ] **DOC-001** Create document, document-version, outbox, and processed-event migrations.
- [ ] **DOC-002** Implement safe storage keys and streaming local file persistence.
- [ ] **DOC-003** Implement extension, media-type, size, and filename validation.
- [ ] **DOC-004** Implement document upload with atomic metadata and outbox creation.
- [ ] **DOC-005** Implement owner/admin metadata read and paginated listing.
- [ ] **DOC-006** Implement content and searchable-metadata updates with monotonic versions.
- [ ] **DOC-007** Implement immediate logical deletion and asynchronous cleanup request.
- [ ] **DOC-008** Implement outbox publishing, retries, and idempotent consumption primitives.
- [ ] **DOC-009** Add lifecycle, ownership, failure-compensation, and concurrent-upload tests.

Exit gate: requirements DOC-01 through DOC-09 pass; upload returns `202` without waiting for indexing.

## Phase 4: Text processing and core index

- [ ] **INDEX-001** Implement bounded TXT, Markdown, and PDF text extraction.
- [ ] **INDEX-002** Implement Unicode normalization, case folding, and deterministic tokenization.
- [ ] **INDEX-003** Implement English stop-word filtering and stemming behind language-aware ports.
- [ ] **INDEX-004** Implement primitive positional postings and immutable term dictionaries.
- [ ] **INDEX-005** Implement document and corpus statistics needed by ranking.
- [ ] **INDEX-006** Implement immutable index generations, atomic publication, and safe reader retention.
- [ ] **INDEX-007** Implement document-version replacement and deletion mutations.
- [ ] **INDEX-008** Implement asynchronous worker state transitions, retries, and terminal failures.
- [ ] **INDEX-009** Enforce duplicate and out-of-order event safety.
- [ ] **INDEX-010** Persist, checksum, recover, and version index snapshots.
- [ ] **INDEX-011** Add algorithm, extraction, recovery, concurrency, and event-ordering tests.

Exit gate: requirements INDEX-01 through INDEX-10 pass and searches continue during index publication.

## Phase 5: Query execution and ranking

- [ ] **SEARCH-001** Implement the query lexer and position-aware syntax errors.
- [ ] **SEARCH-002** Implement the AST parser with parentheses and `NOT`/`AND`/`OR` precedence.
- [ ] **SEARCH-003** Implement implicit-AND term retrieval and Boolean posting-list operations.
- [ ] **SEARCH-004** Implement positional phrase matching.
- [ ] **SEARCH-005** Implement owner, language, type, and date filtering.
- [ ] **SEARCH-006** Implement prefix expansion with bounded candidates.
- [ ] **SEARCH-007** Implement bounded fuzzy expansion using Levenshtein distance.
- [ ] **SEARCH-008** Implement TF ranking, followed by TF-IDF and configured boosts.
- [ ] **SEARCH-009** Implement deterministic pagination and authorization filtering.
- [ ] **SEARCH-010** Expose the versioned search API and response contract.
- [ ] **SEARCH-011** Add parser property tests, reference-engine equivalence tests, authorization tests, and API tests.

Exit gate: requirements SEARCH-01 through SEARCH-10 pass with deterministic results.

## Phase 6: Search experience, caching, and analytics

- [ ] **UX-001** Implement safe, position-aware snippets and highlighting.
- [ ] **UX-002** Implement vocabulary and popular-query Trie autocomplete.
- [ ] **UX-003** Implement spelling suggestions and confidence thresholds.
- [ ] **CACHE-001** Implement generation-keyed local search and autocomplete caches.
- [ ] **CACHE-002** Add Redis adapters and fail-open cache behavior.
- [ ] **ANALYTICS-001** Create search-history persistence and asynchronous recording.
- [ ] **ANALYTICS-002** Implement personal history read/delete operations.
- [ ] **ANALYTICS-003** Implement admin trending and aggregate metrics APIs.
- [ ] **UX-004** Add snippet-XSS, Trie, suggestion, cache, and analytics tests.

Exit gate: SNIP, AUTO, SUGG, and ANALYTICS requirements pass; cache failure cannot fail search.

## Phase 7: Production adapters and hardening

- [ ] **PROD-001** Add Kafka event adapters with partitioning, retry, and dead-letter behavior.
- [ ] **PROD-002** Add deployed Redis configuration and serialization compatibility checks.
- [ ] **PROD-003** Complete upload/parser security limits, CORS, headers, secrets, and audit logging.
- [ ] **PROD-004** Complete Prometheus metrics and operational health behavior.
- [ ] **PROD-005** Add graceful worker shutdown and startup recovery tests.
- [ ] **PROD-006** Add Testcontainers end-to-end coverage for PostgreSQL, Kafka, and Redis.

Exit gate: reference infrastructure flow passes security, recovery, and end-to-end tests.

## Phase 8: Performance validation and release

- [ ] **PERF-001** Build or acquire a documented representative 100,000-document corpus.
- [ ] **PERF-002** Add JMH benchmarks for indexing, posting operations, ranking, Trie, and Levenshtein.
- [ ] **PERF-003** Add reproducible API load tests for search and 100 concurrent uploads.
- [ ] **PERF-004** Profile and optimize posting memory, allocation, locks, and hot query paths.
- [ ] **PERF-005** Publish latency, throughput, cache-hit, concurrency, and memory results with environment details.
- [ ] **REL-001** Verify every specification requirement and definition-of-done item.
- [ ] **REL-002** Complete clean-environment setup, full lifecycle, backup, and recovery rehearsal.
- [ ] **REL-003** Publish version 1 release notes and record deferred stretch goals.

Exit gate: all required acceptance criteria pass, or any exception has measured evidence and an approved documented decision.

## Deferred stretch goals

These are deliberately outside the required sequence and must not displace unfinished version 1 work:

- [ ] BM25 and optional recency scoring
- [ ] Incremental segmented-index compaction
- [ ] Distributed sharding and replica nodes
- [ ] Synonyms and multi-language stemming
- [ ] Semantic search and embeddings
- [ ] Web crawler
- [ ] OpenTelemetry tracing and Grafana dashboards
- [ ] Kubernetes deployment
- [ ] Admin dashboard

## Ledger maintenance rules

1. Mark a task `[>]` before implementation begins.
2. Keep no more than one task `[>]` unless the ledger explicitly records safe parallel execution.
3. Mark a task `[x]` only after its relevant tests or document checks pass.
4. Update the progress counts whenever a task status changes.
5. If blocked, use `[!]` and record the blocker, evidence, and required resolution directly below the task.
6. Do not reorder task IDs silently. Record dependency-driven changes in the change log.
7. Add newly discovered required work at the correct dependency point before starting it.
8. Update this ledger in the same change as the implementation it tracks.

## Change log

| Date | Change |
| --- | --- |
| 2026-07-14 | Created ordered implementation ledger from specification version 1.0.0. |
| 2026-07-14 | Added FOUND-000 for Docker infrastructure; setup is complete but runtime validation is blocked until the Docker daemon is running. |
| 2026-07-14 | Completed FOUND-001 with project metadata, explicit build requirements, dependency policy, and enforced convergence/version checks. |
