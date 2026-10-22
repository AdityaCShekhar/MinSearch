# Session handoff — October 20, 2026

Work is paused at the user's request. Read this file and `docs/TASKS.md` before resuming. Continue REL-001 from the existing working tree; do not restart the implementation.

## State at pause

- Ledger: **72 done, 1 in progress, 2 pending**, excluding stretch goals.
- Current task: **REL-001**, specification and definition-of-done audit, including fixes for previously unwired requirements.
- Pending: **REL-002** clean-environment lifecycle/backup/restore rehearsal; **REL-003** version 1 release notes and deferred goals.
- All changes are uncommitted. The working tree also contains earlier session/user changes and benchmark artifacts. Preserve them; do not reset, clean, or overwrite historical benchmark evidence.
- **The latest working tree has not passed verification.** Passing production/E2E results below predate the newest release-audit changes.
- No implementation or test work should continue until the user resumes. The last two full-test processes have exited with failure; the interrupted targeted test command did not start (`/tmp/minsearch-release-targeted.log` does not exist).

Suggested next-session request: “Read docs/SESSION-HANDOFF.md and finish REL-001, then continue the remaining ledger tasks.”

## Completed production work

| Task | Changes and verification |
| --- | --- |
| PROD-002 | Password-protected internal Redis; Jackson 3 compatibility; versioned typed envelopes and hashed keys; real Redis DTO/list round-trip, TTL, eviction, malformed-schema, bean-selection, and fail-open checks; autocomplete now reads its cache. |
| PROD-003 | Upload content inspection, opaque UUID storage keys, streamed checksums, strict UTF-8, query/token/body/rate limits, heap/time-isolated PDF JVM, exact-origin CORS, headers, production secret guard, current/previous JWT keys, and durable audit tables. Fixed refresh-family revocation rollback and concurrent rotation using PostgreSQL family advisory locks plus row locks. Security, parser, upload, PDF, real HTTP, and concurrent PostgreSQL tests passed. |
| PROD-004 | Actual Prometheus registry, ADMIN-only scraping, bounded route labels, cache/search/index/upload/auth/queue metrics, deployed ECS JSON logs, PostgreSQL/storage/index readiness, and optional Redis health. Full Maven verification and focused metrics/health checks passed. |
| PROD-005 | Shared admission/drain leases, stop-between-item pollers, graceful HTTP/Kafka/scheduler settings, 90-second Compose stop grace, bounded analytics executor, recovery failure tests, and publication/shutdown concurrency tests. Targeted tests passed. |
| PROD-006 | Optional `-Pe2e` reactor module starts real PostgreSQL/Kafka/Redis and all three newly packaged Java 17 services. Both platform scenarios passed without failures/skips, covering auth/ownership, upload/search/metadata update/delete, Redis hits/outage, packaged PDF extraction, terminal PDF failure, cleanup/audit, search restart/tombstones, broker outbox recovery, DB readiness/liveness, and history persistence. |

Operational documentation: `docs/SECURITY.md`, `docs/OPERATIONS.md`, `docs/DOCKER.md`, and `integration-tests/README.md`.

Two issues found while building the E2E harness are already fixed:

1. Docker can reassign ephemeral host ports after restart. `PlatformIT.currentPort()` now inspects the current mapping for HTTP and JDBC.
2. PostgreSQL JDBC could not infer `java.time.Instant` for search-history inserts, and the best-effort path swallowed the failure. Analytics now passes `java.sql.Timestamp`; real persisted history was verified.

## REL-001 changes already written, not yet accepted

The specification audit found earlier ledger items whose helper code existed but was not wired into the deployed flow. The following changes are in the working tree; finish their tests and review before marking the task complete.

### Document lifecycle

- `DocumentContentService` handles multipart content replacement, ownership/admin checks, checksum no-op detection, new opaque keys, monotonic versions, and update outbox events.
- `StoredFileCompensation` registers rollback cleanup, including commit-time rollback. Upload and replacement use it.
- `LocalFilesystemDocumentStorage.store()` now writes a temporary file, forces file contents, and atomically moves it into place.
- `DocumentPersistenceAdapter.save()` flushes metadata and inserts `document_versions`; deletion also writes a visibility fence in the same transaction.
- New document migration **V4__visibility_and_version_history.sql** creates the deletion fence/sequence and backfills the latest known version. It cannot reconstruct historical versions that were never recorded.
- Application services and document controller now support admin reads, paginated listings, mutations, status reads, and content replacement. `ReindexController` exposes the admin reindex route. Security restricts `/api/v1/admin/**` to ADMIN.
- Cleanup deletes keys from all recorded document versions. Review retention of replaced files: they currently remain until document deletion so old indexed versions remain usable.

### Search, analysis, and visibility

- `DocumentVisibilityStore` reads committed deletion fences; search excludes deleted IDs before totals/pagination and includes the fence revision in cache keys.
- Discovery now uses `SearchIndexSnapshot.generationVisible()` for owner/admin scope and deletion filtering. Autocomplete cache keys also include the visibility revision.
- `DocumentIndexPayload` now carries an optional title, retaining the old constructor and schema version for additive compatibility. Verify old event JSON and replay compatibility explicitly.
- `SearchDocument` now carries title and a body-position boundary. Newly indexed title positions start at 0; body positions start at 4096, preventing phrases across fields.
- English indexing applies stop-word removal and stemming while retaining surface aliases at the same positions. Query evaluation applies matching English analysis and preserves phrase gaps. Unsupported languages and legacy projections (`bodyStart == 0`) retain the raw-token path.
- Binary NOT is now enforced by the parser; standalone/unary NOT is rejected. Review Boolean/analysis behavior with mixed languages, stopped terms, phrases, and filters.
- `RankingPolicy` adds validated external title/phrase/exact-term boosts. Search now uses logarithmic TF-IDF, separate title/body frequencies, positive expansion terms, phrase boosts, and exact-term boosts. Cache failure handling computes business work once.

### Search presentation

- New search migration **V7__search_content.sql** stores extracted text by document/version. `JdbcSearchIndexStore` persists content transactionally, keeps text out of the token-recovery projection, and removes content on deletion.
- `SearchResult` now has title, language, file type, version, upload time, and snippet; the two-argument constructor remains for algorithm callers.
- `SearchPresentationService` enriches only the selected page, streams one body at a time through a JDBC cursor, and derives matched surface forms for highlighting.
- `SnippetRenderer` has a linear sliding-window selection after sorting, linear overlap removal, bounded match collection, and token-boundary matching. Latest presentation/renderer edits have not been rerun.
- Review worst-case snippet CPU/memory and concurrent large-body requests. Review the 100000-span and 1000-surface-form caps, dense-window/phrase preference, long phrases, Unicode normalization offsets, and degraded behavior for legacy content without stored text. Do not assume the old helper tests establish the complete HTTP behavior.

## Exact verification boundary

| Evidence | Result and scope |
| --- | --- |
| `/tmp/minsearch-hardening-tests.log` | Full reactor `test` passed after security work, before release-feature edits. |
| `/tmp/minsearch-monitoring-verify.log` | Full reactor `verify` passed before release-feature edits. |
| `/tmp/minsearch-monitoring-final.log` | Focused HTTP/Prometheus, route-cardinality, Redis, and index-health checks passed. |
| `/tmp/minsearch-shutdown-final.log` | Shutdown, publication/recovery, snapshot corruption, analytics, outbox, and PostgreSQL projection checks passed. |
| `/tmp/minsearch-e2e-retry.log` | Last packaged-platform run passed: 2 scenarios, 0 failures/errors/skips, about 56 seconds for the platform test. Command selected no ordinary unit tests because those had already been run; this is not a final full verification of current sources. |
| `/tmp/minsearch-release-lifecycle-compile.log`, `/tmp/minsearch-release-fence-compile.log` | Intermediate release changes compiled; subsequent analysis/ranking/presentation edits came later. |
| `/tmp/minsearch-release-features-tests.log` | Latest full `test` run **failed** in `QueryEvaluatorTest`: stale `alpha AND NOT (beta OR gamma)` fixture, while the parser now implements binary NOT. The fixture has since been changed to `alpha NOT (beta OR gamma)` but not rerun. |
| Search-service Surefire reports | On that last run, `SearchServiceTest` passed after updating the reference formula to `(1 + ln(freq)) * idf + 0.5`; `JdbcSearchIndexStoreTest` and indexing/snippet tests also passed at their then-current source versions. Latest discovery/presentation/renderer edits were applied afterward. |
| `/tmp/minsearch-release-targeted.log` | **Absent.** The last patch applied, but the follow-up test tool call was interrupted before execution. |

Temporary logs may disappear across reboots. The results above are recorded here for continuity, not as a green status for the latest sources. Generated platform logs/reports also live under `integration-tests/target/`; they are ignored build output.

## Resume sequence

1. Inspect current diffs and run the interrupted targeted checks from the repository root:

   ```shell
   ./mvnw test \
     -Dtest=SearchServiceTest,QueryEvaluatorTest,QueryLimitsTest,DocumentIndexingServiceTest,SnippetRendererTest,AutocompleteServiceTest,JdbcSearchIndexStoreTest,DocumentLifecycleServiceTest \
     -Dsurefire.failIfNoSpecifiedTests=false
   ```

2. Add meaningful regressions for the newly wired requirements: content replacement/no-op/version history/rollback cleanup, owner and admin service/API policy, immediate deletion with Kafka unavailable and a warm cache, title-only matches, symmetric English analysis and unsupported languages, phrase gaps and field boundaries, configurable ranking/reference scores, actual snippet metadata/XSS behavior, and additive event/cache serialization compatibility. Extend `PlatformIT` accordingly. Its existing passing scenarios predate these features.
3. Run `./mvnw verify`, then **`./mvnw -Pe2e verify`**. The explicit E2E profile must not silently skip Docker. Use fresh logs for the resumed run. Fix failures; do not rely on earlier production results as evidence for changed code.
4. Finish the requirement-by-requirement audit against `docs/SPECIFICATION.md` and the definition of done. Record evidence and remaining exceptions explicitly. Reconcile the specification's modular-monolith/GET-search description with the existing three-service/POST-search deployment via specification/ADR updates. Check title/body/response/event changes and upgrade/reindex behavior for existing data.
5. Audit build gates: `FOUND-009` historically claims Spotless/Checkstyle/enforcement, but the current root POM still lacks those gates. Formatting/static analysis/dependency vulnerability reporting and an OpenAPI 3.1 contract check need reconciliation. Security dependency scanning has not been completed.
6. Reassess performance after the functional changes. Existing Oct 19/20 benchmarks describe earlier implementations and synthetic short TXT workloads. They do not prove the final 100k-document p95 target, representative PDF/natural-document throughput, or post-body upload SLO. Preserve all historical evidence; put new measurements in a new result directory.
7. Complete REL-002 with an isolated clean-start/lifecycle and actual PostgreSQL-plus-source backup/restore rehearsal, including pending outbox work, durable token projection, tombstones, and a cold cache. This has **not** been implemented or run. Decide and verify how in-flight broker records are preserved/replayed during restore.
8. Complete REL-003 with release notes that distinguish verified behavior, remaining acceptance gaps, required exceptions, and optional stretch goals. Do not mark version 1 releasable unless the ledger exit gate is actually met or exceptions are explicitly approved.

Additional audit points: Kafka indexing currently uses fixed rather than exponential retry backoff; the binary snapshot utility is tested but deployed restart recovery uses PostgreSQL; extraction settings are largely individual `@Value` properties rather than a complete validated configuration model; an unused legacy `JwtAuthenticationFilter` remains. Review these against the specification instead of assuming prior completion claims cover them.

## Environment and preservation

- Workspace: `/Users/aditya/Projects/minsearch`; Java 17; macOS/zsh; Docker Desktop available in verified runs.
- Maven/Testcontainers/embedded HTTP tests need permission to access Docker and local sockets outside the restricted sandbox. Existing Maven command approvals may be reusable.
- No subagents were used or requested.
- Historical evidence is under `benchmarks/results/2026-10-19` and `benchmarks/results/2026-10-20`. Do not regenerate their manifests from current code or overwrite their source/artifact hashes.
- Many benchmark/index changes predate the production-hardening work. Consult the ledger change log and diffs before attributing changes or preparing commits.
