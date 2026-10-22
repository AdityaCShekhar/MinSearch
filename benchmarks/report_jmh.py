#!/usr/bin/env python3
"""Render measured JMH results and matched before/after comparisons."""

import argparse
import json
from pathlib import Path


def key(result):
    return result["benchmark"], tuple(sorted(result.get("params", {}).items()))


def label(result):
    return result["benchmark"].split(".")[-2] + "." + result["benchmark"].split(".")[-1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    root = args.directory
    baseline = {key(item): item for item in json.loads((root / "baseline-jmh.json").read_text())}
    optimized = json.loads((root / "comparison/jmh.json").read_text())
    complete = json.loads((root / "full/jmh.json").read_text())
    lines = ["# JMH measurements", "",
             "Single-thread measurements on the Apple M4 Pro reference host, Microsoft OpenJDK 17.0.16+8-LTS, two JVM forks, `-Xms512m -Xmx2g`, JMH 1.37 with the GC profiler. HTTP benchmarks were not running concurrently. Raw JSON retains iterations, allocation rates, and confidence intervals.", "",
             "## Matched algorithm comparison", "",
             "Both builds use two 500 ms warm-up iterations and three 500 ms measurements per fork. Entries show JMH mean ± its 99.9% confidence error. Ratios compare means from this run; they are not guaranteed speedups, and the baseline 10,000-document build has a particularly wide interval.", "",
             "| Benchmark | Documents | Before | After | Units | Observed mean ratio | Before B/op | After B/op |",
             "| --- | ---: | ---: | ---: | --- | ---: | ---: | ---: |"]
    for item in optimized:
        before = baseline[key(item)]
        first, second = before["primaryMetric"], item["primaryMetric"]
        if first["scoreUnit"] != second["scoreUnit"]:
            raise ValueError("Mismatched comparison units")
        allocation = lambda result: result["secondaryMetrics"]["gc.alloc.rate.norm"]["score"]
        lines.append(f"| {label(item)} | {item['params']['documents']} | {first['score']:.3f} ± {first['scoreError']:.3f} | {second['score']:.3f} ± {second['scoreError']:.3f} | {second['scoreUnit']} | {first['score'] / second['score']:.2f}× | {allocation(before):,.0f} | {allocation(item):,.0f} |")
    lines.extend(["", "The new-document guard avoids scanning existing postings for absent IDs during bulk reconstruction. Phrase evaluation uses binary UUID lookup in sorted posting lists instead of repeatedly scanning them. The retained patch identifies these algorithm changes. Streaming JDBC recovery is measured by the HTTP fixture track, not by these microbenchmarks.", "",
                  "## Complete optimized suite", "",
                  "Defaults: three one-second warm-ups and five one-second measurements per fork. The corpus here is the fixed text in Fixture.java repeated for the requested document count; it differs from the HTTP corpus. JMH uses Fixture's compact vocabulary; the HTTP corpus additionally has one unique marker per document.", "",
                  "| Benchmark | Parameters | Mean | 99.9% error | Units | Allocated B/op |",
                  "| --- | --- | ---: | ---: | --- | ---: |"])
    for item in complete:
        metric = item["primaryMetric"]
        allocation = item["secondaryMetrics"]["gc.alloc.rate.norm"]["score"]
        parameters = ", ".join(f"{name}={value}" for name, value in item.get("params", {}).items())
        lines.append(f"| {label(item)} | {parameters} | {metric['score']:.3f} | {metric['scoreError']:.3f} | {metric['scoreUnit']} | {allocation:,.1f} |")
    lines.extend(["", "Generation build excludes persistence, file extraction/tokenization, and publication after each document. Cached ranking measures a prewarmed cache lookup. Uncached ranking and autocomplete always compute their result. Microbenchmark time is not API latency or end-to-end ingestion throughput. See the benchmark guide and retained runtime/source/artifact metadata for reproduction.", ""])
    (root / "JMH-REPORT.md").write_text("\n".join(lines))


if __name__ == "__main__":
    main()
