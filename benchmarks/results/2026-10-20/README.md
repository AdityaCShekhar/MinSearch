# Kafka hardening and engine profiling — 20 October 2026

Completed PROD-001 and PERF-004. The production and release exit gates still depend on the separate pending hardening and release tasks.

## Matched JMH measurements

Apple M4 Pro, Microsoft OpenJDK 17.0.16, JMH 1.37, 512 MiB initial / 2 GiB maximum heap. Each case uses two fresh JVM forks, two 500 ms warm-ups, three 500 ms measurements per fork, and GC allocation profiling. Both versions use the same benchmark fixtures and settings. Values are measured mean ± JMH's 99.9% confidence error; short-run ratios are local observations.

The incremental fixture contains the common benchmark text plus one unique term per document. Each operation constructs a new immutable generation against the same fixed populated source. The query fixture uses the original common-text corpus. These microbenchmarks exclude persistence, messaging, source extraction, HTTP, atomic publication, and document-metadata publication.

| Operation | Documents | Before | After | Unit | Mean ratio | Before B/op | After B/op |
| --- | ---: | ---: | ---: | --- | ---: | ---: | ---: |
| IndexMutationBenchmark.delete | 1000 | 0.217 ± 0.020 | 0.185 ± 0.011 | ms/op | 1.18× | 876,990 | 633,299 |
| IndexMutationBenchmark.delete | 10000 | 2.570 ± 0.395 | 2.283 ± 0.136 | ms/op | 1.13× | 8,499,558 | 5,983,483 |
| IndexMutationBenchmark.delete | 100000 | 78.318 ± 5.847 | 57.021 ± 25.310 | ms/op | 1.37× | 85,425,328 | 60,279,917 |
| IndexMutationBenchmark.insert | 1000 | 0.201 ± 0.004 | 0.157 ± 0.005 | ms/op | 1.28× | 992,767 | 776,962 |
| IndexMutationBenchmark.insert | 10000 | 2.176 ± 0.378 | 1.804 ± 0.523 | ms/op | 1.21× | 9,592,486 | 7,356,293 |
| IndexMutationBenchmark.insert | 100000 | 64.666 ± 4.695 | 52.759 ± 3.112 | ms/op | 1.23× | 97,488,548 | 73,942,947 |
| IndexMutationBenchmark.replace | 1000 | 0.246 ± 0.007 | 0.192 ± 0.001 | ms/op | 1.28× | 871,967 | 640,394 |
| IndexMutationBenchmark.replace | 10000 | 2.653 ± 0.190 | 2.132 ± 0.051 | ms/op | 1.24× | 8,386,768 | 5,990,801 |
| IndexMutationBenchmark.replace | 100000 | 85.358 ± 21.988 | 65.385 ± 4.711 | ms/op | 1.31× | 84,232,069 | 60,287,322 |
| SearchAlgorithmsBenchmark.phraseRetrieval | 1000 | 148.007 ± 12.485 | 84.315 ± 5.802 | us/op | 1.76× | 327,979 | 186,513 |
| SearchAlgorithmsBenchmark.phraseRetrieval | 10000 | 5241.978 ± 305.119 | 3520.374 ± 159.048 | us/op | 1.49× | 4,537,886 | 1,740,194 |
| SearchAlgorithmsBenchmark.phraseRetrieval | 100000 | 93352.668 ± 7796.290 | 78542.879 ± 8505.183 | us/op | 1.19× | 49,289,473 | 20,142,474 |
| SearchAlgorithmsBenchmark.rankingUncached | 1000 | 271.049 ± 36.023 | 78.803 ± 4.797 | us/op | 3.44× | 306,240 | 146,337 |
| SearchAlgorithmsBenchmark.rankingUncached | 10000 | 6956.978 ± 442.690 | 1254.964 ± 109.454 | us/op | 5.54× | 2,970,972 | 1,383,993 |
| SearchAlgorithmsBenchmark.rankingUncached | 100000 | 108653.561 ± 12753.001 | 32460.356 ± 5080.272 | us/op | 3.35× | 31,900,276 | 15,396,643 |

## Mixed engine profile

One 20-second run per version, 10,000 documents, four uncached readers and two actual indexing-service writers. Readers query a fixed synthetic generation with a unique term per document and a 75%/20%/5% mix of 64/256/1,024-token documents. Writers update a separate index through real extraction, checksumming, tokenization, synchronization, and atomic publication, with an in-memory persistence port. Known query totals are checked before recording. This is an engine workload, not the live Kafka pipeline or an API latency trial.

| Observation | Before | After |
| --- | ---: | ---: |
| Query operations/s | 669.74 | 2,800.35 |
| Index mutations/s | 319.71 | 253.29 |
| Pre-workload post-GC heap, MiB | 29.72 | 18.98 |
| Recorded monitor waits ≥1 ms | 592 | 5,069 |
| p95 of recorded monitor waits, ms | 142.53 | 4.80 |
| Maximum recorded monitor wait, ms | 497.54 | 28.95 |

The heap reading includes fixture and JVM objects and is not an exact retained-index size. JFR CPU and allocation events are samples; their weights are not exact allocation totals. The lock percentiles include only recorded waits of at least 1 ms, not all acquisitions. Moving independent extraction outside the publication lock shortened the observed longer stalls, and publication remains serialized. Writer throughput fell by 20.8% under the unrestricted reader load in this run; this tradeoff is retained rather than assuming every workload improves. Reader throughput and workload mix affect contention, so these one-run observations do not establish production concurrency guarantees.

## Changes supported by the profile

- Share unchanged immutable posting lists and copy only modified lists when publishing a generation.
- Store longer position lists in primitive arrays while preserving the immutable List contract and snapshot format. Keep compact JDK single/pair lists for short postings; phrase lookup avoids boxing new positions and uses binary membership for packed lists.
- Filter authorization over query candidates, cache immutable corpus counts and sorted vocabulary, seek prefix candidates, and reuse bounded-distance rows.
- Select the best candidates for the requested page with a bounded heap, preserving score/time/UUID order and total counts. Large page offsets use overflow-safe arithmetic.
- Extract outside the writer monitor and recheck document versions inside it; persist and publish the index and owner metadata together.

## Delivery and correctness verification

`./mvnw -Pbenchmarks verify`: **106 tests**, zero failures/errors/skips. Python harness: **8 checks**, zero failures/errors. The Java tests include a full-sort ranking/pagination reference, a full-matrix fuzzy reference, owner filtering with Boolean complements, shared-generation immutability, defensive primitive positions, blocked-persistence readers, concurrent durable versions, deletion ordering, replay validation, and failed broker sends.

The isolated real-Kafka rehearsal forced a missing source file, restored it, and replayed the exact document dead letter twice; one successful completion was created. It also forced an outcome transaction failure, repaired it, and replayed the outcome twice. Finally it stopped Kafka, observed an outbox retry, restarted the broker, and reached INDEXED. Its source and service image identities are retained separately because it ran before the performance changes; the final HTTP regression below exercises the optimized application.

The final optimized application also passed the retained [HTTP regression](http/1000/summary.json): 1,000 real uploads, 100 client workers, all query-result and cache-regime controls, and concurrent search. Its raw observations and resource/image/source provenance are under `http/`. These short regression trials complement the larger historical benchmark matrix; they do not replace it.

## Evidence and reproduction

- `before-jmh/` and `after-jmh/`: all 15 matched cases per version, raw iterations, confidence errors, allocations, logs, source and artifact hashes.
- `before-profile/` and `after-profile/`: raw JFR recordings, operation counts, sampled-frame summaries, JVM and source/artifact identity.
- `delivery/`: real Kafka replay receipts, failure/recovery logs, service image identities, and successful disposal of the isolated stack.
- `performance-changes.patch`: the exact difference between the two production algorithm versions, including formatting. Reverse it in a separate checkout to reconstruct the baseline engine; preserve the new benchmark fixtures.
- `source-manifest.json` and `SHA256SUMS`: source and retained-artifact integrity.

Use the [benchmark guide](../../README.md) for commands and scope, and the [Docker replay guide](../../../docs/DOCKER.md#replay-one-dead-letter-record) for operations. Re-render this report with `python3 benchmarks/report_completion.py benchmarks/results/2026-10-20`. Check artifacts with `shasum -a 256 -c SHA256SUMS` from this directory.

The remaining costs include whole-generation dictionary/document-map copies and serialized publication. Natural-document/PDF behavior, large-scale real ingestion, distributed operation, and the specification's latency/release gates remain outside these results. Incremental segmented compaction remains a deferred stretch goal.
