# Reference benchmark results — 19 October 2026

Measured on an Apple M4 Pro, 24 GiB RAM, 14 logical CPUs. HTTP search ran in Docker with a **2-CPU quota, 3 GiB memory limit, and 2,304 MiB maximum Java heap**. JMH ran as a separate single-thread host JVM with a 2 GiB maximum heap. These are local synthetic-workload measurements.

## What was measured

- **397,674 authenticated search requests across 54 trials**, with zero HTTP/result errors and all server cache-counter checks passing. Three trials cover each combination of 1,000/10,000/100,000 documents, cold/warm/mixed caches, and 1/16 clients.
- **100 simultaneous upload clients:** 999 measured TXT uploads were accepted with zero errors; all became searchable in **27.74 seconds**, or **36.01 documents/s**. Upload p95 was **1,904.82 ms**. One bootstrap document is excluded from these timers. All **28/28** sampled existing-document availability probes passed.
- **100,000-document search and recovery:** the persisted-token fixture became ready in **20.49 seconds**. This track bypasses uploads, Kafka, extraction, and tokenization; it is **not a 100,000-document ingestion result**.
- All **19 JMH benchmark/parameter combinations** completed, covering generation construction, tokenization, posting intersection, phrase retrieval, cached/uncached ranking, Trie autocomplete, spelling suggestions, and Levenshtein, with allocation profiling and two JVM forks.

The corpus is deterministic short ASCII TXT: seed `20261019`, 83.2 mean tokens and 739.69 mean source bytes per document. Full corpus SHA-256: `3085d1e149f92a0df71d097a6d3658ff832d20c096af637a3e53337430b9e7ba`.

## Measured algorithm improvements

Matched JMH settings: two forks, two 500 ms warm-ups, three 500 ms measurements per fork. Values are mean ± JMH's 99.9% confidence error; ratios compare measured means on this machine.

| Operation, 10,000 documents | Before ms/op | After ms/op | Mean ratio |
| --- | ---: | ---: | ---: |
| Build one immutable generation | 1,705.13 ± 1,072.90 | 26.20 ± 2.02 | 65.07× |
| Evaluate positional phrase | 204.29 ± 14.18 | 5.09 ± 0.32 | 40.15× |

The build change skips removal scans for IDs absent from the index. Phrase matching uses binary lookup in sorted posting lists. Phrase allocation fell from **6,779,788 to 4,537,884 B/op** in this comparison. The baseline build interval is wide; these short measurements establish an observed improvement, not a universal speedup guarantee. Generation construction excludes persistence, extraction, tokenization, and incremental publication after each document.

## Search at 100,000 documents

Pooled successful requests across three trials, nearest-rank percentiles. Latency includes HTTP transmission, response reading, and client JSON decoding.

| Cache regime | Clients | p50 ms | p95 ms | p99 ms | Requests/s |
| --- | ---: | ---: | ---: | ---: | ---: |
| Cold | 1 | 61.29 | 212.22 | 252.93 | 12.11 |
| Warm | 1 | 0.59 | 1.05 | 1.67 | 1,432.00 |
| Cold | 16 | 1,090.41 | 3,480.34 | 4,122.28 | 11.64 |
| Warm | 16 | 2.47 | 39.95 | 65.79 | 2,886.70 |

The uncached concurrent workload saturates this resource budget. Warm-cache throughput primarily describes HTTP/authentication/cache access. These results do not establish the specification's network-excluded latency SLO, production capacity, or natural-document/PDF performance. At 100,000 documents the sampled heap peak was **2,150.55 MiB** and Docker working-set peak was **2,825.22 MiB**; neither is retained-index size.

## Reports and evidence

- [Complete HTTP report](HTTP-REPORT.md): all sizes and cache regimes, query breakdown, load/recovery results, memory/CPU observations, methodology, and reproduction commands.
- [JMH report](JMH-REPORT.md): matched comparisons, complete optimized suite, confidence errors, and allocations.
- [`http/`](http): raw gzip request samples, per-trial summaries, corpus manifest, exact runtime/image/resource facts, telemetry, and per-stage invocation metadata.
- [`baseline-jmh.json`](baseline-jmh.json), [`comparison/`](comparison), and [`full/`](full): JMH observations and source/artifact/runtime provenance. The baseline uses the original algorithms; [the retained patch](algorithm-optimizations.patch) identifies the changes.
- [Validation summary](validation.json): 90 Java tests passed without failures or skips; seven Python harness checks passed before measurement.
- [`diagnostics/warm-ttl-boundary/`](diagnostics/warm-ttl-boundary): an excluded invalid trial and its summary. Cache validation caught old warm entries expiring; the 100,000-document stage was rerun with fresh phase-specific keys. The valid 1,000/10,000 stages and corrected 100,000 stage use the same application source hash and service image.
- [Source manifest](source-manifest.json) records the production and harness file hashes. `SHA256SUMS` records retained artifact checksums. Large corpus/projection files are regenerated rather than committed.

Check retained artifact integrity from this directory with `shasum -a 256 -c SHA256SUMS` (or `sha256sum -c SHA256SUMS` on Linux).

## Resume wording supported by this evidence

- Built reproducible benchmarks covering 100,000 synthetic TXT search fixtures, 54 authenticated HTTP trials totaling 397,674 successful requests, and 100 concurrent upload clients.
- Optimized immutable index construction and positional phrase retrieval, reducing measured 10,000-document JMH means from 1,705 to 26.2 ms and 204 to 5.09 ms through scan elimination and binary posting lookup.

Keep the workload and JMH qualifiers when citing these numbers. See the [benchmark guide](../../README.md) to reproduce the suite.
