#!/usr/bin/env python3
"""Render the retained matched profiling and hardening evidence."""

import argparse
import json
from pathlib import Path


def read(root, name):
    return json.loads((root / name).read_text())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    root = args.directory
    before = read(root, "before-jmh/jmh.json")
    after = read(root, "after-jmh/jmh.json")
    identity = lambda row: (row["benchmark"], row["params"]["documents"])
    original = {identity(row): row for row in before}
    if set(original) != {identity(row) for row in after}:
        raise ValueError("Before/after benchmark cases differ")
    lines = ["# Kafka hardening and engine profiling — 20 October 2026", "",
             "Completed PROD-001 and PERF-004. The production and release exit gates still depend on the separate pending hardening and release tasks.", "",
             "## Matched JMH measurements", "",
             "Apple M4 Pro, Microsoft OpenJDK 17.0.16, JMH 1.37, 512 MiB initial / 2 GiB maximum heap. Each case uses two fresh JVM forks, two 500 ms warm-ups, three 500 ms measurements per fork, and GC allocation profiling. Both versions use the same benchmark fixtures and settings. Values are measured mean ± JMH's 99.9% confidence error; short-run ratios are local observations.", "",
             "The incremental fixture contains the common benchmark text plus one unique term per document. Each operation constructs a new immutable generation against the same fixed populated source. The query fixture uses the original common-text corpus. These microbenchmarks exclude persistence, messaging, source extraction, HTTP, atomic publication, and document-metadata publication.", "",
             "| Operation | Documents | Before | After | Unit | Mean ratio | Before B/op | After B/op |",
             "| --- | ---: | ---: | ---: | --- | ---: | ---: | ---: |"]
    for row in after:
        old = original[identity(row)]
        first, second = old["primaryMetric"], row["primaryMetric"]
        if first["scoreUnit"] != second["scoreUnit"]:
            raise ValueError("Measurement units differ")
        allocation = lambda item: item["secondaryMetrics"]["gc.alloc.rate.norm"]["score"]
        label = ".".join(row["benchmark"].split(".")[-2:])
        lines.append(f"| {label} | {row['params']['documents']} | {first['score']:.3f} ± {first['scoreError']:.3f} | {second['score']:.3f} ± {second['scoreError']:.3f} | {second['scoreUnit']} | {first['score'] / second['score']:.2f}× | {allocation(old):,.0f} | {allocation(row):,.0f} |")
    old, new = read(root, "before-profile/summary.json"), read(root, "after-profile/summary.json")
    old_events, new_events = read(root, "before-profile/events-summary.json"), read(root, "after-profile/events-summary.json")
    writer_change = (new['mutations'] / new['elapsedSeconds']) / (old['mutations'] / old['elapsedSeconds']) - 1
    writer_observation = f"Writer throughput {'rose' if writer_change >= 0 else 'fell'} by {abs(writer_change):.1%} under the unrestricted reader load in this run; this tradeoff is retained rather than assuming every workload improves."
    lines.extend(["", "## Mixed engine profile", "",
                  "One 20-second run per version, 10,000 documents, four uncached readers and two actual indexing-service writers. Readers query a fixed synthetic generation with a unique term per document and a 75%/20%/5% mix of 64/256/1,024-token documents. Writers update a separate index through real extraction, checksumming, tokenization, synchronization, and atomic publication, with an in-memory persistence port. Known query totals are checked before recording. This is an engine workload, not the live Kafka pipeline or an API latency trial.", "",
                  "| Observation | Before | After |", "| --- | ---: | ---: |",
                  f"| Query operations/s | {old['queries'] / old['elapsedSeconds']:,.2f} | {new['queries'] / new['elapsedSeconds']:,.2f} |",
                  f"| Index mutations/s | {old['mutations'] / old['elapsedSeconds']:,.2f} | {new['mutations'] / new['elapsedSeconds']:,.2f} |",
                  f"| Pre-workload post-GC heap, MiB | {old['retainedHeapBytes'] / 1024**2:.2f} | {new['retainedHeapBytes'] / 1024**2:.2f} |",
                  f"| Recorded monitor waits ≥1 ms | {old_events['event_counts'].get('jdk.JavaMonitorEnter', 0):,} | {new_events['event_counts'].get('jdk.JavaMonitorEnter', 0):,} |",
                  f"| p95 of recorded monitor waits, ms | {old_events['recorded_monitor_wait_ms']['p95']:.2f} | {new_events['recorded_monitor_wait_ms']['p95']:.2f} |",
                  f"| Maximum recorded monitor wait, ms | {old_events['recorded_monitor_wait_ms']['max']:.2f} | {new_events['recorded_monitor_wait_ms']['max']:.2f} |", "",
                  "The heap reading includes fixture and JVM objects and is not an exact retained-index size. JFR CPU and allocation events are samples; their weights are not exact allocation totals. The lock percentiles include only recorded waits of at least 1 ms, not all acquisitions. Moving independent extraction outside the publication lock shortened the observed longer stalls, and publication remains serialized. " + writer_observation + " Reader throughput and workload mix affect contention, so these one-run observations do not establish production concurrency guarantees.", "",
                  "## Changes supported by the profile", "",
                  "- Share unchanged immutable posting lists and copy only modified lists when publishing a generation.",
                  "- Store longer position lists in primitive arrays while preserving the immutable List contract and snapshot format. Keep compact JDK single/pair lists for short postings; phrase lookup avoids boxing new positions and uses binary membership for packed lists.",
                  "- Filter authorization over query candidates, cache immutable corpus counts and sorted vocabulary, seek prefix candidates, and reuse bounded-distance rows.",
                  "- Select the best candidates for the requested page with a bounded heap, preserving score/time/UUID order and total counts. Large page offsets use overflow-safe arithmetic.",
                  "- Extract outside the writer monitor and recheck document versions inside it; persist and publish the index and owner metadata together.", "",
                  "## Delivery and correctness verification", ""])
    validation = read(root, "validation.json")
    delivery = read(root, "delivery/validation.json")
    if not delivery["valid"]:
        raise ValueError("Delivery rehearsal did not pass")
    lines.extend([f"`./mvnw -Pbenchmarks verify`: **{validation['java']['tests']} tests**, zero failures/errors/skips. Python harness: **{validation['python']['tests']} checks**, zero failures/errors. The Java tests include a full-sort ranking/pagination reference, a full-matrix fuzzy reference, owner filtering with Boolean complements, shared-generation immutability, defensive primitive positions, blocked-persistence readers, concurrent durable versions, deletion ordering, replay validation, and failed broker sends.", "",
                  "The isolated real-Kafka rehearsal forced a missing source file, restored it, and replayed the exact document dead letter twice; one successful completion was created. It also forced an outcome transaction failure, repaired it, and replayed the outcome twice. Finally it stopped Kafka, observed an outbox retry, restarted the broker, and reached INDEXED. Its source and service image identities are retained separately because it ran before the performance changes; the final HTTP regression below exercises the optimized application.", ""])
    if (root / "http/1000/summary.json").exists():
        http = read(root, "http/1000/summary.json")
        if not http["valid"]:
            raise ValueError("HTTP regression did not pass")
        lines.extend(["The final optimized application also passed the retained [HTTP regression](http/1000/summary.json): 1,000 real uploads, 100 client workers, all query-result and cache-regime controls, and concurrent search. Its raw observations and resource/image/source provenance are under `http/`. These short regression trials complement the larger historical benchmark matrix; they do not replace it.", ""])
    lines.extend(["## Evidence and reproduction", "",
                  "- `before-jmh/` and `after-jmh/`: all 15 matched cases per version, raw iterations, confidence errors, allocations, logs, source and artifact hashes.",
                  "- `before-profile/` and `after-profile/`: raw JFR recordings, operation counts, sampled-frame summaries, JVM and source/artifact identity.",
                  "- `delivery/`: real Kafka replay receipts, failure/recovery logs, service image identities, and successful disposal of the isolated stack.",
                  "- `performance-changes.patch`: the exact difference between the two production algorithm versions, including formatting. Reverse it in a separate checkout to reconstruct the baseline engine; preserve the new benchmark fixtures.",
                  "- `source-manifest.json` and `SHA256SUMS`: source and retained-artifact integrity.", "",
                  "Use the [benchmark guide](../../README.md) for commands and scope, and the [Docker replay guide](../../../docs/DOCKER.md#replay-one-dead-letter-record) for operations. Re-render this report with `python3 benchmarks/report_completion.py benchmarks/results/2026-10-20`. Check artifacts with `shasum -a 256 -c SHA256SUMS` from this directory.", "",
                  "The remaining costs include whole-generation dictionary/document-map copies and serialized publication. Natural-document/PDF behavior, large-scale real ingestion, distributed operation, and the specification's latency/release gates remain outside these results. Incremental segmented compaction remains a deferred stretch goal.", ""])
    (root / "README.md").write_text("\n".join(lines))


if __name__ == "__main__":
    main()
