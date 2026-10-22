# Reproducible benchmarks

This suite measures the real search engine with JMH and its authenticated HTTP API with isolated Docker Compose stacks. It keeps raw observations, correctness checks, cache counters, corpus checksums, and environment details.

## Requirements

- JDK 17, Python 3.9+, Docker, and Docker Compose supporting `!override` (2.24.4+; measured with 2.40.3).
- The normal application configuration: copy `.env.example` to `.env` if needed. Default benchmark ports are **18081–18083**; leave these free.
- Allow about 8 GiB for the benchmark stack and Docker overhead. The current resource limits total about 6.6 GiB across three PostgreSQL instances and the other containers; historical reports record their own deployment budgets. The 100,000-document fixture materializes about 656 MiB on disk, in addition to its database and corpus.

Run commands from the repository root. No Python packages are required.

## Run the HTTP benchmark

```shell
python3 -u benchmarks/run.py --sizes 1000 10000 100000 \
  --upload-documents 1000 --upload-concurrency 100 \
  --concurrency 1 16 --requests 256 --seconds 5 --repetitions 3 \
  --output benchmarks/output/my-run
python3 benchmarks/report.py benchmarks/output/my-run \
  --output benchmarks/output/my-run/REPORT.md
```

The runner builds the three service images, creates fresh `minsearch-bench-*` projects and volumes, registers an account, and checks every measured response. It removes its disposable stacks in `finally`; the development databases and volumes are separate. SQL fixtures and fault injection explicitly select the owning auth, document, or search database. Historical reports produced with the earlier shared PostgreSQL deployment are retained as recorded and should not be treated as measurements of this topology. Use `--skip-build` only when the benchmark images already match the source. `--keep-stack` is available for a single size when diagnosing a failure. Generated corpora, fixtures, and local run output are ignored by Git.

**Two loading tracks are deliberately reported separately:**

| Size | Track | What it establishes |
| ---: | --- | --- |
| 1,000 | Real multipart HTTP uploads, PostgreSQL outbox, Kafka, extraction, tokenization, indexing | Upload latency with 100 client workers, time until all accepted documents are searchable, and availability of an existing document during ingestion |
| 10,000 / 100,000 | Direct persisted-token projection followed by service restart and index recovery | Query behavior and startup recovery at that index size |

The fixture track bypasses document storage, upload, Kafka, extraction, tokenization, and incremental index construction. **It does not measure 100,000-document ingestion throughput.** Each loading track runs once per invocation. The ingest timer excludes one bootstrap document and ends at search visibility; it does not wait for asynchronous document-status feedback to finish. Availability probes run once per second and establish visibility at those sampled instants.

### Workloads and calculations

- Eight query classes cover rare terms, Boolean AND, phrases, metadata filters, prefix expansion, fuzzy expansion, binary NOT, and no matches. Expected totals are derived from the generated corpus; top-20 page length and a stable generation are also checked.
- Each workload runs with 1 and 16 persistent, closed-loop HTTP clients, three trials, at least 256 requests **and** five measured seconds per trial. Ten rounds over all query classes and two requests per worker are excluded as warm-up.
- Cold phases use distinct raw cache keys by changing only the casing of an absent OR term. Query normalization keeps meaning, length, and selectivity fixed. Warm phases reuse eight prewarmed keys; each phase gets a fresh namespace so an older entry's expire-after-write TTL cannot cross the measured interval. Mixed phases rotate cold groups across all classes, targeting 75% hits.
- Actual server cache-counter deltas must match every request's intended regime. Errors, incorrect totals/pages, changing generations, or cache mismatches invalidate the stage. Warm throughput includes authentication and the cache path.
- Latency includes HTTP transmission, response reading, and Python JSON decoding. Pooled successful-request percentiles use nearest rank (`ceil(p*n)-1`); throughput divides successful requests by measured wall time. Raw failures are retained separately.
- JVM heap and Docker working set / CPU are sampled during the run. Sampled peaks are not retained-index size or allocation measurements. Telemetry gaps during an intentional restart are retained.
- The host also runs the load generator. This is a local closed-loop workload, not a dedicated open-loop saturation test, a production capacity guarantee, or the specification's network-excluded engine latency SLO.

### Corpus protocol

```shell
python3 benchmarks/corpus.py --documents 100000 --seed 20261019 \
  --tokens 64 --output benchmarks/output/corpus
```

Version 1 generates synthetic ASCII TXT with deterministic per-document seeds, a unique marker, one of 16 topics, one of four phrases, and fixed-vocabulary filler. The default distribution is 80% 64-token, 15% 128-token, and 5% 256-token documents: **83.2 tokens per document**. These short, repetitive documents help expose selectivity and algorithm costs; they do not represent PDF extraction, large uploads, or natural-language relevance quality. Larger corpora preserve smaller corpora as exact prefixes. The manifest includes the complete JSONL SHA-256, vocabulary, byte/token counts, seed, and query expectations. Projection fixtures also record their corpus-prefix checksum.

## Run JMH

```shell
./mvnw -Pbenchmarks -pl benchmarks/jmh -am -DskipTests package
mkdir -p benchmarks/output
java -jar benchmarks/jmh/target/benchmarks.jar -prof gc \
  -rf json -rff benchmarks/output/jmh.json
```

To retain runtime, source and artifact hashes, and the complete JMH log automatically, use the wrapper (the output directory must be new):

```shell
python3 benchmarks/run_jmh.py --output benchmarks/output/jmh-run
```

Pass optional JMH arguments after `--`, for example `-- -wi 2 -w 500ms -i 3 -r 500ms -f 2`. The wrapper always enables allocation profiling and JSON output.

The optional Maven profile leaves ordinary builds unchanged. Benchmarks call the actual Java tokenizer, immutable generation builder, posting intersection, phrase evaluation, TF-IDF ranking, Trie autocomplete, spelling suggestions, and Levenshtein implementation. Cached and uncached ranking are separate; uncached paths use a cache implementation that always computes. Results are returned to JMH to prevent dead-code elimination.

Defaults: two fresh JVM forks, three one-second warm-up iterations, five one-second measurements, `-Xms512m -Xmx2g`, and 1,000/10,000-document index parameters. JMH's GC profiler records normalized allocated bytes per operation. JMH is a single-thread microbenchmark here; it excludes HTTP, Spring startup, persistence, Kafka, and incremental publication after every document. Do not run it concurrently with the HTTP workload. See the [official JMH project](https://github.com/openjdk/jmh) for harness documentation.

The retained before/after comparison uses identical, shorter settings for both versions:

```shell
java -jar benchmarks/jmh/target/benchmarks.jar \
  '.*(IndexBuildBenchmark.buildGeneration|SearchAlgorithmsBenchmark.phraseRetrieval)' \
  -p documents=1000,10000 -wi 2 -w 500ms -i 3 -r 500ms -f 2 \
  -prof gc -rf json -rff benchmarks/output/comparison-jmh.json
```

For reproducing the original algorithms, apply the retained `algorithm-optimizations.patch` in reverse to a separate checkout of the benchmark revision, rebuild, and run the same command. Do not reverse a patch over unrelated working changes. Record the source/artifact hashes and Java runtime for each build. Confidence intervals and raw per-fork measurements are in the JMH JSON; short-run speedup ratios are observations on this machine.

## Allocation, contention, and incremental publication

After building the benchmark JAR, run the bounded JFR workload:

```shell
python3 benchmarks/run_profile.py --documents 10000 --seconds 20 \
  --output benchmarks/output/profile-run
```

It checks query totals, then profiles four uncached readers on a fixed generation and
two actual `DocumentIndexingService` writers on a separate generation. The reader
fixture has a unique term per document and a deterministic 64/256/1,024-token length
mix, including positions beyond the JVM's small-integer cache. Writers use the normal
file extraction, checksum, tokenization, synchronization, and publication path with an
in-memory persistence port. It excludes database and Kafka time. These are synthetic
fixtures, not a natural-document or PDF workload.

The wrapper retains the recording, runtime/source/artifact hashes, operation counts,
and sampled CPU/allocation/monitor summaries. The pre-workload post-GC heap reading
includes fixture and JVM objects; it is an approximate process-heap comparison, not
an exact retained-index measurement. JFR monitor events cover waits of at least 1 ms;
sample counts are not latency percentiles or proof that no shorter contention exists.

For incremental insert/replace/delete costs and uncached query paths at all three sizes:

```shell
python3 benchmarks/run_jmh.py --output benchmarks/output/mutation-run -- \
  '.*(IndexMutationBenchmark.*|SearchAlgorithmsBenchmark.rankingUncached|SearchAlgorithmsBenchmark.phraseRetrieval)' \
  -p documents=1000,10000,100000 -wi 2 -w 500ms -i 3 -r 500ms -f 2
```

The mutation fixture adds a unique term per document to the common text and builds
a new generation for one mutation against a fixed populated source. It measures
immutable index construction, without atomic publication, metadata publication,
extraction, persistence, or messaging. Run JMH, JFR, and
HTTP loads separately so they do not contend for host resources.

## Retained results

The initial reference run is under [`results/2026-10-19`](results/2026-10-19).
The subsequent [hardening and profiling results](results/2026-10-20) retain matched
incremental mutation/query benchmarks, CPU/allocation/lock recordings, and real Kafka
failure/replay evidence. Each run records its own source and artifact identity; the
initial measurements describe the earlier implementation.

The reports explain measured outcomes and limitations. Small raw samples and environment files are retained; regenerate the large corpus and projection files from the documented seed. Re-render the initial HTTP results with:

```shell
python3 benchmarks/report.py benchmarks/results/2026-10-19/http \
  --output benchmarks/results/2026-10-19/HTTP-REPORT.md
```
