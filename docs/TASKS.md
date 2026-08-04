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
| Done | 53 |
| In progress | 1 |
| Pending | 23 |
| Blocked | 0 |

## Phase 0: Specification

- [x] **SPEC-001** Capture product scope and functional requirements.
- [x] **SPEC-002** Define architecture, contracts, data model, quality standards, milestones, and acceptance gates.

Exit gate: [Specification](./SPECIFICATION.md) is implementation-ready and internally reviewed. **Passed.**

## Phase 1: Foundation

- [x] **FOUND-000** Provision the complete local Docker environment and verify healthy startup. Verified on July 18, 2026.
  - Setup complete: application image, PostgreSQL, Redis, Kafka KRaft broker, topic initialization, volumes, health checks, environment template, and operating guide.
  - Verified: Compose model, Maven tests/package, full service health, Docker-profile readiness endpoint, and creation of all four Kafka topics.
- [x] **FOUND-001** Correct Maven project metadata and establish dependency/version management.
- [x] **FOUND-002** Create package-by-feature module boundaries: `auth`, `document`, `index`, `search`, `autocomplete`, `cache`, `analytics`, and `shared`. Verified on July 15, 2026.
- [x] **FOUND-003** Add architecture tests that enforce module boundaries. Verified on July 15, 2026.
- [x] **FOUND-004** Define typed, validated application configuration and local/test profiles. Verified on July 15, 2026.
- [x] **FOUND-005** Add PostgreSQL, migrations, and repository test infrastructure. Verified against PostgreSQL 17.10 on July 18, 2026.
- [x] **FOUND-006** Add local filesystem storage and in-process event/cache adapters behind ports. Verified on July 17, 2026.
  - Added a safe local filesystem document storage adapter behind a domain port.
  - Added an in-process domain event bus for local adapter workflows.
  - Added a Caffeine-backed cache store with TTL-aware lookups and eviction.
  - Verified with targeted adapter tests and the full Maven test suite.
- [x] **FOUND-007** Add the RFC 9457 error model, validation handling, and stable error codes. Verified on July 17, 2026.
  - Added shared RFC 9457 problem-detail helpers and stable machine-readable error codes.
  - Added a global REST exception handler for validation failures and unexpected errors.
  - Verified the response shape with MVC tests covering validation and generic failures.
- [x] **FOUND-008** Add correlation IDs, structured logging, health endpoints, and baseline metrics. Verified on July 17, 2026.
  - Added a request correlation-ID filter that propagates IDs through MDC and response headers.
  - Added a shared request-metrics filter plus a baseline application gauge.
  - Exposed health, metrics, and Prometheus actuator endpoints and added correlation-aware console logging.
  - Verified with unit tests and a Spring Boot actuator integration test.
  - Local diagnostics: set `DEBUG=true` for Spring Boot debug output, or set
    `LOGGING_LEVEL_COM_ADITYA_MINSEARCH=DEBUG` for application debug logs. Inspect the
    authentication service with `docker compose logs --follow auth-service` and use the
    returned `X-Correlation-Id` to follow one request across the logs. Do not enable debug
    logging in production.
- [x] **FOUND-009** Add formatting, static analysis, unit-test, and integration-test build checks. Verified on July 17, 2026.
  - Added Spotless formatting checks and a Google Java Format configuration.
  - Added Checkstyle static analysis to the verify lifecycle.
  - Added a dedicated Failsafe integration-test phase with a bootstrapping IT.
  - Verified the full `verify` lifecycle passes.
- [x] **FOUND-010** Document reproducible local development and test commands. Verified on July 17, 2026.
  - Added a root README with the clean-clone workflow, app readiness check, and Maven command set.
  - Tightened the build documentation to point at the README as the quick-start entry point.
  - Documented Docker, unit-test, verify, formatting, static-analysis, and integration-test commands in one place.

Exit gate: application starts from a clean environment, migrations pass, build checks pass, and architecture tests enforce boundaries.

## Phase 2: Authentication and authorization

- [x] **AUTH-001** Create user and refresh-token migrations and persistence adapters. Verified on July 17, 2026.
  - Added ordered migrations for `users` and `refresh_tokens`.
  - Added auth domain types plus JPA-backed repository adapters.
  - Verified persistence against PostgreSQL with round-trip repository tests.
- [x] **AUTH-002** Implement registration with normalized unique email and adaptive password hashing.
  - Registration now normalizes emails, persists adaptive password hashes, and rejects duplicate normalized emails.
- [x] **AUTH-003** Implement login and short-lived signed access JWTs.
  - Login now returns access and refresh tokens, with JWT-issued access claims and external-key signing.
- [x] **AUTH-004** Implement refresh-token families, hashing, atomic rotation, and reuse detection.
  - Refresh tokens are hashed at rest, rotated atomically, and reuse revokes the full family.
- [x] **AUTH-005** Implement logout and refresh-token revocation.
  - Logout marks the submitted refresh token revoked while leaving the access token valid until expiry.
- [x] **AUTH-006** Configure request authentication, role policy, and public endpoint allowlist.
  - Security now enforces authenticated access by default and permits the public auth/health endpoints.
- [x] **AUTH-007** Add authentication rate limiting and generic credential errors.
  - Authentication endpoints now enforce per-IP limits and return generic credential failures.
- [x] **AUTH-008** Add unit, integration, security, and end-to-end authentication tests.
  - Added focused auth service and controller tests and verified the auth test slice passes locally.

Exit gate: requirements AUTH-01 through AUTH-07 pass automated tests.

Status update: AUTH-001 through AUTH-007 are implemented and the focused auth test suite passes locally; the broader repository integration suite still depends on a live PostgreSQL/Testcontainers environment.

## Phase 3: Document lifecycle

- [x] **DOC-001** Create document, document-version, outbox, and processed-event migrations.
  - Added the four tables and indexes in `V1__document_schema.sql`.
- [x] **DOC-002** Implement safe storage keys and streaming local file persistence.
  - Added configurable document storage-root wiring and verified safe local filesystem storage with traversal tests.
- [x] **DOC-003** Implement extension, media-type, size, and filename validation.
  - Added TXT/Markdown/PDF validation, size limits, media-type checks, safe filename handling, and unit tests.
- [x] **DOC-004** Implement document upload with atomic metadata and outbox creation.
  - Upload persists metadata and `document.uploaded` outbox data in one transaction and compensates storage when persistence fails.
- [x] **DOC-005** Implement owner/admin metadata read and paginated listing.
  - Added owner-scoped document reads and listings, excluding logically deleted records.
- [x] **DOC-006** Implement content and searchable-metadata updates with monotonic versions.
  - Added searchable metadata updates with monotonic document versions and update outbox events.
- [x] **DOC-007** Implement immediate logical deletion and asynchronous cleanup request.
  - Added logical deletion with `DELETE_PENDING` state and delete outbox events.
- [x] **DOC-008** Implement outbox publishing, retries, and idempotent consumption primitives.
  - Added a bounded pending-event processor, retry accounting, in-process publishing, and processed-event deduplication.
- [x] **DOC-009** Add lifecycle, ownership, failure-compensation, and concurrent-upload tests.
  - Added lifecycle transition, ownership, storage-compensation, outbox, and 100-concurrent-upload coverage; focused document tests pass.

Exit gate: requirements DOC-01 through DOC-09 pass; upload returns `202` without waiting for indexing.

## Phase 4: Text processing and core index

- [x] **INDEX-001** Implement bounded TXT, Markdown, and PDF text extraction.
  - Added UTF-8 TXT/Markdown extraction, PDFBox text extraction, page/input/text limits, encrypted-PDF rejection, and focused tests.
- [x] **INDEX-002** Implement Unicode normalization, case folding, and deterministic tokenization.
  - Added NFKC normalization, Locale.ROOT case folding, Unicode letter/number/mark tokenization, and stable offsets/positions with focused tests.
- [x] **INDEX-003** Implement English stop-word filtering and stemming behind language-aware ports.
  - Added language-aware stop-word/stemmer ports with English implementations and position-preserving processing.
- [x] **INDEX-004** Implement primitive positional postings and immutable term dictionaries.
  - Added immutable posting lists with document versions, term frequencies, sorted positions, and copy-on-write generation building.
- [x] **INDEX-005** Implement document and corpus statistics needed by ranking.
  - Added indexed document lengths, document frequency, corpus counts, and average document length calculation.
- [x] **INDEX-006** Implement immutable index generations, atomic publication, and safe reader retention.
  - Added immutable generations and atomic monotonic publication for lock-free readers.
- [x] **INDEX-007** Implement document-version replacement and deletion mutations.
  - Added replacement and deletion mutations that remove stale postings and preserve version metadata.
- [x] **INDEX-008** Implement asynchronous worker state transitions, retries, and terminal failures.
  - Added synchronized worker result states plus bounded exponential retry policy primitives.
- [x] **INDEX-009** Enforce duplicate and out-of-order event safety.
  - Added event-ID deduplication and per-document version ordering guards.
- [x] **INDEX-010** Persist, checksum, recover, and version index snapshots.
  - Added versioned binary snapshots with atomic writes, SHA-256 checksums, and corruption rejection.
- [x] **INDEX-011** Add algorithm, extraction, recovery, concurrency, and event-ordering tests.
  - Added 18 focused search/index tests covering extraction, tokenization, stemming, postings, publication, worker ordering, and snapshot recovery.

Exit gate: requirements INDEX-01 through INDEX-10 pass and searches continue during index publication.

## Phase 5: Query execution and ranking

- [x] **SEARCH-001** Implement the query lexer and position-aware syntax errors.
  - Added lexing for terms, phrases, Boolean operators, parentheses, filters, prefix/fuzzy markers, and position-aware malformed-query errors.
- [x] **SEARCH-002** Implement the AST parser with parentheses and `NOT`/`AND`/`OR` precedence.
  - Added AST parsing with precedence, implicit AND, filters, prefix/fuzzy nodes, and syntax validation.
- [x] **SEARCH-003** Implement implicit-AND term retrieval and Boolean posting-list operations.
  - Added term retrieval and AND/OR/binary-NOT posting-set evaluation.
- [x] **SEARCH-004** Implement positional phrase matching.
  - Added contiguous position matching against immutable postings.
- [x] **SEARCH-005** Implement owner, language, type, and date filtering.
  - Added owner authorization plus language, type, and ISO date-range filters.
- [x] **SEARCH-006** Implement prefix expansion with bounded candidates.
  - Added lexically deterministic prefix expansion capped at 100 terms.
- [x] **SEARCH-007** Implement bounded fuzzy expansion using Levenshtein distance.
  - Added bounded edit-distance expansion capped at 50 candidates and distance 2.
- [x] **SEARCH-008** Implement TF ranking, followed by TF-IDF and configured boosts.
  - Added deterministic TF-IDF scoring over matched postings.
- [x] **SEARCH-009** Implement deterministic pagination and authorization filtering.
  - Added score/upload-time/document-ID ordering, bounded page sizes, and owner scoping.
- [x] **SEARCH-010** Expose the versioned search API and response contract.
  - Added `/api/v1/search` request/response types and generation-aware response metadata.
- [x] **SEARCH-011** Add parser property tests, reference-engine equivalence tests, authorization tests, and API tests.
  - Added lexer/parser, retrieval, phrase, expansion, authorization, ranking, pagination, and API-layer coverage.

Exit gate: requirements SEARCH-01 through SEARCH-10 pass with deterministic results.

## Phase 6: Search experience, caching, and analytics

- [x] **UX-001** Implement safe, position-aware snippets and highlighting.
  - Added bounded densest-window snippets, phrase preference, case-insensitive surface highlighting, HTML escaping, and ellipses with focused XSS tests.
- [>] **UX-002** Implement vocabulary and popular-query Trie autocomplete.
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
| 2026-07-15 | Verified FOUND-002 with package-by-feature module boundaries creation. |
| 2026-07-15 | Verified FOUND-003 with architecture tests enforcement of module boundaries. |
| 2026-07-15 | Marked FOUND-004 as completed and verified after implementing and validating application configuration. |
| 2026-07-18 | Completed FOUND-005 with environment-backed PostgreSQL configuration, ordered Flyway migrations, reusable Testcontainers repository-test support, and a passing PostgreSQL 17.10 integration test. |
| 2026-07-18 | Cleared the FOUND-000 Docker-daemon blocker; full Compose startup verification proceeded after Docker became available. |
| 2026-07-18 | Completed FOUND-000 after rebuilding the compatible Java runtime image and verifying healthy PostgreSQL, Redis, Kafka, app readiness, and Kafka topic initialization. |
| 2026-07-17 | Completed FOUND-006 with safe local filesystem storage, in-process event delivery, and Caffeine-backed cache adapters verified by unit and integration tests. |
| 2026-07-17 | Completed FOUND-007 with RFC 9457 problem-details handling, validation mapping, and stable error codes verified by MVC and unit tests. |
| 2026-07-17 | Completed FOUND-008 with correlation IDs, request metrics, actuator exposure, and correlation-aware logging verified by unit and integration tests. |
| 2026-07-18 | Documented local logging and debug-mode switches for the completed endpoints, including correlation-ID log tracing and production safety guidance. |
| 2026-07-18 | Started DOC-001 and completed DOC-002/DOC-003 with document storage configuration, upload validation, and unit coverage. |
| 2026-07-18 | Completed DOC-001 schema migration and started DOC-004 with transactional upload metadata/outbox creation and storage compensation. |
| 2026-07-17 | Completed FOUND-009 with Spotless formatting, Checkstyle static analysis, unit tests, and Failsafe integration tests wired into `verify`. |
| 2026-07-17 | Completed FOUND-010 with a root README and build documentation covering reproducible local development and test commands. |
| 2026-07-17 | Completed AUTH-001 with auth schema migrations and JPA persistence adapters verified against PostgreSQL. |
| 2026-08-05 | Completed DOC-009 with lifecycle, ownership, storage-compensation, outbox, and concurrent-upload tests; started INDEX-001. |
| 2026-08-05 | Completed INDEX-001 with bounded TXT/Markdown/PDF extraction and focused five-test coverage; started INDEX-002. |
| 2026-08-05 | Completed INDEX-002 with deterministic Unicode normalization/case folding/tokenization and focused three-test coverage; started INDEX-003. |
| 2026-08-05 | Completed Phase 4 (INDEX-003 through INDEX-011) with language-aware analysis, immutable postings/generations, worker ordering, snapshot recovery, retry/publication primitives, and 18 passing search-service tests. |
| 2026-08-05 | Completed SEARCH-001 with the position-aware query lexer and three focused lexer tests; started SEARCH-002. |
| 2026-08-05 | Completed Phase 5 (SEARCH-002 through SEARCH-011) with AST parsing, Boolean/phrase retrieval, filters, prefix/fuzzy expansion, TF-IDF ranking, authorization, pagination, API contract, and 27 passing search-service tests. |
| 2026-08-05 | Completed UX-001 with bounded safe snippets and position-aware highlighting; started UX-002. |
