# JMH measurements

Single-thread measurements on the Apple M4 Pro reference host, Microsoft OpenJDK 17.0.16+8-LTS, two JVM forks, `-Xms512m -Xmx2g`, JMH 1.37 with the GC profiler. HTTP benchmarks were not running concurrently. Raw JSON retains iterations, allocation rates, and confidence intervals.

## Matched algorithm comparison

Both builds use two 500 ms warm-up iterations and three 500 ms measurements per fork. Entries show JMH mean ± its 99.9% confidence error. Ratios compare means from this run; they are not guaranteed speedups, and the baseline 10,000-document build has a particularly wide interval.

| Benchmark | Documents | Before | After | Units | Observed mean ratio | Before B/op | After B/op |
| --- | ---: | ---: | ---: | --- | ---: | ---: | ---: |
| IndexBuildBenchmark.buildGeneration | 1000 | 12.465 ± 1.276 | 1.942 ± 0.144 | ms/op | 6.42× | 7,729,962 | 7,689,531 |
| IndexBuildBenchmark.buildGeneration | 10000 | 1705.133 ± 1072.902 | 26.203 ± 2.025 | ms/op | 65.07× | 78,267,804 | 77,902,141 |
| SearchAlgorithmsBenchmark.phraseRetrieval | 1000 | 1965.028 ± 121.373 | 140.363 ± 10.796 | us/op | 14.00× | 544,071 | 327,978 |
| SearchAlgorithmsBenchmark.phraseRetrieval | 10000 | 204293.838 ± 14184.931 | 5088.584 ± 318.771 | us/op | 40.15× | 6,779,788 | 4,537,884 |

The new-document guard avoids scanning existing postings for absent IDs during bulk reconstruction. Phrase evaluation uses binary UUID lookup in sorted posting lists instead of repeatedly scanning them. The retained patch identifies these algorithm changes. Streaming JDBC recovery is measured by the HTTP fixture track, not by these microbenchmarks.

## Complete optimized suite

Defaults: three one-second warm-ups and five one-second measurements per fork. The corpus here is the fixed text in Fixture.java repeated for the requested document count; it differs from the HTTP corpus. JMH uses Fixture's compact vocabulary; the HTTP corpus additionally has one unique marker per document.

| Benchmark | Parameters | Mean | 99.9% error | Units | Allocated B/op |
| --- | --- | ---: | ---: | --- | ---: |
| EditDistanceBenchmark.levenshtein | term=distributed | 113.198 | 1.521 | ns/op | 864.0 |
| EditDistanceBenchmark.levenshtein | term=transactional | 119.375 | 1.649 | ns/op | 1,008.0 |
| EditDistanceBenchmark.levenshtein | term=internationalization | 204.472 | 2.466 | ns/op | 1,512.0 |
| IndexBuildBenchmark.buildGeneration | documents=1000 | 1.985 | 0.056 | ms/op | 7,705,243.8 |
| IndexBuildBenchmark.buildGeneration | documents=10000 | 25.549 | 0.693 | ms/op | 78,101,990.3 |
| SearchAlgorithmsBenchmark.phraseRetrieval | documents=1000 | 139.608 | 3.313 | us/op | 327,888.8 |
| SearchAlgorithmsBenchmark.phraseRetrieval | documents=10000 | 5066.560 | 104.244 | us/op | 4,537,854.1 |
| SearchAlgorithmsBenchmark.postingIntersection | documents=1000 | 71.314 | 1.248 | us/op | 238,520.4 |
| SearchAlgorithmsBenchmark.postingIntersection | documents=10000 | 1804.523 | 33.197 | us/op | 2,260,362.7 |
| SearchAlgorithmsBenchmark.rankingCached | documents=1000 | 0.071 | 0.001 | us/op | 136.0 |
| SearchAlgorithmsBenchmark.rankingCached | documents=10000 | 0.071 | 0.001 | us/op | 136.0 |
| SearchAlgorithmsBenchmark.rankingUncached | documents=1000 | 264.117 | 11.410 | us/op | 306,177.6 |
| SearchAlgorithmsBenchmark.rankingUncached | documents=10000 | 6705.928 | 140.900 | us/op | 2,970,927.8 |
| SearchAlgorithmsBenchmark.spellingSuggestions | documents=1000 | 2.046 | 0.027 | us/op | 9,384.0 |
| SearchAlgorithmsBenchmark.spellingSuggestions | documents=10000 | 2.201 | 0.047 | us/op | 9,384.0 |
| SearchAlgorithmsBenchmark.tokenize | documents=1000 | 2.350 | 0.040 | us/op | 4,896.0 |
| SearchAlgorithmsBenchmark.tokenize | documents=10000 | 2.311 | 0.050 | us/op | 4,896.0 |
| SearchAlgorithmsBenchmark.trieAutocompleteUncached | documents=1000 | 1.879 | 0.498 | us/op | 22,108.0 |
| SearchAlgorithmsBenchmark.trieAutocompleteUncached | documents=10000 | 1.692 | 0.228 | us/op | 22,144.0 |

Generation build excludes persistence, file extraction/tokenization, and publication after each document. Cached ranking measures a prewarmed cache lookup. Uncached ranking and autocomplete always compute their result. Microbenchmark time is not API latency or end-to-end ingestion throughput. See the benchmark guide and retained runtime/source/artifact metadata for reproduction.
