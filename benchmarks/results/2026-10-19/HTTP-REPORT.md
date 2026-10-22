# MiniSearch HTTP benchmark results

Run started: `2026-10-18T20:02:57.631400+00:00`. Application source SHA-256: `c3694a0e14ec92f348e126691a11aab6c35434684339e8ee1c9cb023da373ab3`.

## Run provenance

- The 1,000- and 10,000-document stages come from the first sustained invocation. Their response and cache-counter checks all passed. Per-stage run-environment.json retains the exact invocation.
- The first 100,000-document invocation was rejected: a warm trial recorded 52 cache misses among 945 requests when older entries crossed their two-minute expire-after-write TTL. This was a cache-control harness failure; its summary and failed raw trial are retained under diagnostics/warm-ttl-boundary and excluded from measured tables.
- The 100,000-document stage was rerun in a fresh stack after assigning new warm-cache key namespaces per phase. The application source, service images, corpus protocol, quotas, search client counts, minimum duration/request count, and trial count were unchanged. Its own run-environment.json records the rerun.
- Exact cache-key variants: the 1,000/10,000 stages used hot variant 0 and cold variant 1 + phase_number * variant_stride + request_index. The corrected 100,000 stage used hot variant 1 + phase_number * variant_stride and cold variant hot_variant + 1 + request_index. Both use the measured_query protocol in corpus.py; all variants normalize to the same query. The per-stage cache counters confirm the intended regimes.

## Environment

- Host: Apple M4 Pro; macOS-27.0.1-arm64-arm-64bit; 14 logical CPUs.
- Host RAM: 24.00 GiB. Docker: 28.5.2, 14 CPUs, 7.65 GiB.
- Search container: 2 CPU quota, 3 GiB memory limit, Java 17, `-Xms512m -Xmx2304m -XX:+ExitOnOutOfMemoryError`.
- Search JVM runtime: OpenJDK Runtime Environment Temurin-17.0.20.1+1 (build 17.0.20.1+1).
- Document container: 1 CPU / 768 MiB / 384 MiB heap. Auth: 0.5 CPU / 512 MiB / 256 MiB heap. PostgreSQL: 1 CPU / 512 MiB. Kafka: 1 CPU / 768 MiB / 512 MiB heap.
- Other development containers were idle but running on the same Docker VM. This is one local machine, without CPU pinning or a dedicated load generator.
- Exact image IDs, container resource limits, raw samples, and application source identity are retained with the run.

## Corpus and methodology

- Deterministic synthetic short ASCII TXT documents, seed `20261019`, 100,000 generated documents, 83.20 mean tokens, 739.69 mean source bytes.
- Complete JSONL corpus SHA-256: `3085d1e149f92a0df71d097a6d3658ff832d20c096af637a3e53337430b9e7ba`. The manifest includes the vocabulary and expected query counts.
- Query classes: rare term, Boolean AND, phrase, type filter, bounded prefix, bounded fuzzy, binary NOT, and no-match; assigned round-robin with counts differing by at most one per phase.
- 3 trials per workload/client count; at least 256 requests and 5.0 measured seconds per trial. 10 untimed rounds over all query classes plus per-phase/per-client warm-up.
- Closed-loop persistent HTTP/1.1 clients on host loopback. Latency includes request sending, response reading, and client JSON decoding; it is not network-excluded engine time or an open-loop capacity test.
- Cold-cache requests use distinct casing of an absent OR term. Parsing/normalization preserves the same query meaning and selectivity. Warm requests reuse prewarmed keys. Mixed phases rotate cold groups across query classes, targeting 75% reuse.
- Cache regimes are checked against actual server hit/miss counter deltas. Every response is checked for total count, top-20 page size, and a stable index generation. Any error or cache mismatch invalidates the stage.
- Percentiles below pool successful raw requests across trials and use nearest rank, `ceil(p*n)-1`. Throughput is successful requests divided by the sum of measured trial wall times. Errors are reported separately.
- Resource sampling runs during the workload. Docker memory is its reported working set; heap usage comes from JVM metrics. Peaks are sampled observations, not retained-index size or allocation rates.

## Loading and recovery

| Documents | Loading track | Upload workers | Max observed active requests | Upload p95 ms | Searchable docs/s | Seconds to searchable / ready | Errors |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1,000 | Real upload + Kafka + indexing | 100 | 100 | 1,904.82 | 36.01 | 27.74 | 0 |
| 10,000 | Token-projection fixture + startup recovery | n/a | n/a | n/a | n/a | 14.96 | 0 |
| 100,000 | Token-projection fixture + startup recovery | n/a | n/a | n/a | n/a | 20.49 | 0 |

During the 1,000-document ingest track, 28/28 existing-document probes passed; probe p95 was 21.71 ms, with 0 errors.

Each loading track was executed once; the repeated trials below apply to search workloads. The real ingest track excludes one bootstrap document and measures the remaining accepted documents until the full corpus is visible in search. Upload latency includes transfer time for these small files. Concurrent once-per-second probes verify that the bootstrap document remains searchable at sampled instants. Fixture tracks bypass upload, metadata persistence, Kafka, extraction, tokenization, and incremental per-document index construction; their ready time includes Compose dependency checks, JVM startup, and reconstruction. They establish search behavior at that index size, not end-to-end ingest throughput.

## Search HTTP results

| Documents | Cache regime | Clients | Requests | p50 ms | p95 ms | p99 ms | Requests/s | Measured hit rate | Errors |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1,000 | cold | 1 | 16126 | 0.81 | 1.48 | 2.12 | 1,074.81 | 0.0% | 0 |
| 1,000 | warm | 1 | 24793 | 0.53 | 0.86 | 1.45 | 1,652.65 | 100.0% | 0 |
| 1,000 | mixed | 1 | 22427 | 0.57 | 1.05 | 1.57 | 1,494.90 | 75.0% | 0 |
| 1,000 | cold | 16 | 35664 | 2.56 | 59.76 | 69.73 | 2,358.27 | 0.0% | 0 |
| 1,000 | warm | 16 | 60078 | 2.36 | 5.85 | 41.91 | 3,999.17 | 100.0% | 0 |
| 1,000 | mixed | 16 | 52951 | 2.38 | 6.55 | 50.21 | 3,508.72 | 75.0% | 0 |
| 10,000 | cold | 1 | 3367 | 3.62 | 9.34 | 11.05 | 224.39 | 0.0% | 0 |
| 10,000 | warm | 1 | 23800 | 0.54 | 0.91 | 1.49 | 1,586.42 | 100.0% | 0 |
| 10,000 | mixed | 1 | 9485 | 0.71 | 4.83 | 9.11 | 632.11 | 75.0% | 0 |
| 10,000 | cold | 16 | 5239 | 12.22 | 98.73 | 107.03 | 344.50 | 0.0% | 0 |
| 10,000 | warm | 16 | 56937 | 2.38 | 5.79 | 51.17 | 3,793.85 | 100.0% | 0 |
| 10,000 | mixed | 16 | 18795 | 3.12 | 77.18 | 84.50 | 1,238.99 | 75.0% | 0 |
| 100,000 | cold | 1 | 768 | 61.29 | 212.22 | 252.93 | 12.11 | 0.0% | 0 |
| 100,000 | warm | 1 | 21483 | 0.59 | 1.05 | 1.67 | 1,432.00 | 100.0% | 0 |
| 100,000 | mixed | 1 | 768 | 0.82 | 85.25 | 231.96 | 47.93 | 75.0% | 0 |
| 100,000 | cold | 16 | 768 | 1,090.41 | 3,480.34 | 4,122.28 | 11.64 | 0.0% | 0 |
| 100,000 | warm | 16 | 43420 | 2.47 | 39.95 | 65.79 | 2,886.70 | 100.0% | 0 |
| 100,000 | mixed | 16 | 805 | 92.29 | 1,396.44 | 2,994.25 | 46.43 | 75.2% | 0 |

## Uncached query breakdown, one client

Successful requests pooled across cold-cache trials. This separates expensive broad queries from narrow lookups; it uses the same raw requests as the aggregate table.

| Documents | Query class | Requests | p50 ms | p95 ms | p99 ms | Errors |
| ---: | --- | ---: | ---: | ---: | ---: | ---: |
| 1,000 | rare_term | 2017 | 0.68 | 1.19 | 1.77 | 0 |
| 1,000 | boolean | 2017 | 0.75 | 1.27 | 1.88 | 0 |
| 1,000 | phrase | 2017 | 0.87 | 1.58 | 2.41 | 0 |
| 1,000 | filter | 2016 | 0.76 | 1.35 | 1.96 | 0 |
| 1,000 | prefix | 2016 | 0.81 | 1.42 | 1.98 | 0 |
| 1,000 | fuzzy | 2015 | 0.79 | 1.41 | 1.95 | 0 |
| 1,000 | negative | 2014 | 1.15 | 1.88 | 2.58 | 0 |
| 1,000 | missing | 2014 | 0.68 | 1.23 | 1.73 | 0 |
| 10,000 | rare_term | 422 | 2.53 | 3.71 | 4.59 | 0 |
| 10,000 | boolean | 422 | 3.46 | 4.90 | 5.98 | 0 |
| 10,000 | phrase | 422 | 5.07 | 6.91 | 8.96 | 0 |
| 10,000 | filter | 421 | 3.55 | 4.89 | 5.54 | 0 |
| 10,000 | prefix | 420 | 3.51 | 4.96 | 5.95 | 0 |
| 10,000 | fuzzy | 420 | 3.67 | 5.17 | 6.68 | 0 |
| 10,000 | negative | 420 | 8.97 | 11.09 | 13.29 | 0 |
| 10,000 | missing | 420 | 2.60 | 3.92 | 5.33 | 0 |
| 100,000 | rare_term | 96 | 41.59 | 84.52 | 167.33 | 0 |
| 100,000 | boolean | 96 | 62.40 | 94.27 | 172.39 | 0 |
| 100,000 | phrase | 96 | 93.88 | 157.67 | 201.46 | 0 |
| 100,000 | filter | 96 | 67.40 | 109.62 | 180.61 | 0 |
| 100,000 | prefix | 96 | 48.42 | 104.78 | 138.51 | 0 |
| 100,000 | fuzzy | 96 | 56.28 | 155.63 | 196.07 | 0 |
| 100,000 | negative | 96 | 203.80 | 279.36 | 356.14 | 0 |
| 100,000 | missing | 96 | 40.63 | 62.02 | 148.62 | 0 |

## Sampled search-process resources

| Documents | Heap peak MiB | Docker working-set peak MiB | Docker CPU peak (100% = one core) | Samples | Telemetry errors |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1,000 | 401.13 | 885.40 | 210.07 | 28 | 3 |
| 10,000 | 595.83 | 1,206.27 | 210.56 | 25 | 4 |
| 100,000 | 2,150.55 | 2,825.22 | 212.50 | 55 | 10 |

Telemetry gaps can occur while the search container is intentionally stopped/restarted or before a GC-pause meter exists; they are retained in resources.json. See timestamps and phases when interpreting peaks.

## Scope of the evidence

These results cover one search instance, a synthetic short-TXT corpus, local plaintext HTTP, and the stated client workloads. They do not establish distributed scaling, natural-language relevance quality, PDF or 10 MiB upload performance, production availability, or the specification's network-excluded latency SLO. Warm cache numbers primarily measure the HTTP/authentication/cache path. The load generator shares the host and may limit warm-cache throughput. p99 values from short trials are exploratory tails, not production guarantees.

## Reproduce

```shell
./mvnw -Pbenchmarks -pl benchmarks/jmh -am -DskipTests package
python3 -u benchmarks/run.py --sizes 1000 10000 100000 --requests 256 --repetitions 3 --concurrency 1 16
python3 benchmarks/report.py benchmarks/output/<run-directory> --output benchmarks/output/<run-directory>/REPORT.md
```

Each run uses fresh minsearch-bench-* containers and named volumes; it removes only those disposable resources afterwards. It never uses the development database. Generated corpora and materialization files remain in benchmarks/output, which is ignored by Git.
