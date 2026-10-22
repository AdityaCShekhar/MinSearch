#!/usr/bin/env python3
"""Render a report from retained samples; never infer measurements from configuration."""

import argparse
import gzip
import json
from pathlib import Path
import re

from run import summarize


def memory_bytes(value):
    match = re.fullmatch(r"\s*([\d.]+)\s*([KMGT]?i?B)\s*", value)
    if not match:
        raise ValueError("Unrecognized Docker memory unit: " + value)
    units = {"B": 1, "kB": 1000, "KB": 1000, "MB": 1000**2, "GB": 1000**3, "TB": 1000**4,
             "KiB": 1024, "MiB": 1024**2, "GiB": 1024**3, "TiB": 1024**4}
    return float(match.group(1)) * units[match.group(2)]


def resource_summary(directory):
    data = json.loads((directory / "resources.json").read_text())
    heap = [sample["heap_used_bytes"] for sample in data["samples"] if "heap_used_bytes" in sample]
    container, cpu = [], []
    for sample in data["samples"]:
        for item in sample.get("docker", []):
            if "-search-service-" in item["Name"]:
                container.append(memory_bytes(item["MemUsage"].split("/")[0].strip()))
                cpu.append(float(item["CPUPerc"].rstrip("%")))
    return {"samples": len(data["samples"]), "telemetry_errors": len(data["errors"]),
            "heap_peak_mib": max(heap) / 1024**2 if heap else None,
            "search_working_set_peak_mib": max(container) / 1024**2 if container else None,
            "search_cpu_peak_percent_one_core_units": max(cpu) if cpu else None}


def number(value, digits=2):
    return "n/a" if value is None else f"{value:,.{digits}f}"


def generate_report(source, destination):
    environment = json.loads((source / "environment.json").read_text())
    corpus = json.loads((source / "corpus/manifest.json").read_text())
    args = environment["arguments"]
    directories = sorted((path for path in source.iterdir() if path.is_dir() and path.name.isdigit()), key=lambda path: int(path.name))
    runtime = "See each stage's java-runtime.json"
    if directories and (directories[0] / "java-runtime.json").exists():
        version = json.loads((directories[0] / "java-runtime.json").read_text())["version"]
        runtime = next((line for line in version.splitlines() if "Runtime Environment" in line), runtime)
    lines = ["# MiniSearch HTTP benchmark results", "",
             f"Run started: `{environment['utc']}`. Application source SHA-256: `{environment['application_source_sha256']}`.", "",
             "## Environment", "",
             f"- Host: {environment.get('host_cpu_model', environment['architecture'])}; {environment['platform']}; {environment['client_logical_cpus']} logical CPUs.",
             f"- Host RAM: {number(environment.get('host_memory_bytes', 0) / 1024**3)} GiB. Docker: {environment['docker']['ServerVersion']}, {environment['docker']['NCPU']} CPUs, {number(environment['docker']['MemTotal'] / 1024**3)} GiB.",
             "- Search container: 2 CPU quota, 3 GiB memory limit, Java 17, `-Xms512m -Xmx2304m -XX:+ExitOnOutOfMemoryError`.",
             f"- Search JVM runtime: {runtime}.",
             "- Document container: 1 CPU / 768 MiB / 384 MiB heap. Auth: 0.5 CPU / 512 MiB / 256 MiB heap. PostgreSQL: 1 CPU / 512 MiB. Kafka: 1 CPU / 768 MiB / 512 MiB heap.",
             "- Other development containers were idle but running on the same Docker VM. This is one local machine, without CPU pinning or a dedicated load generator.",
             "- Exact image IDs, container resource limits, raw samples, and application source identity are retained with the run.", "",
             "## Corpus and methodology", "",
             f"- Deterministic synthetic short ASCII TXT documents, seed `{corpus['seed']}`, {corpus['documents']:,} generated documents, {number(corpus['tokens_mean'])} mean tokens, {number(corpus['bytes_mean'])} mean source bytes.",
             f"- Complete JSONL corpus SHA-256: `{corpus['sha256']}`. The manifest includes the vocabulary and expected query counts.",
             "- Query classes: rare term, Boolean AND, phrase, type filter, bounded prefix, bounded fuzzy, binary NOT, and no-match; assigned round-robin with counts differing by at most one per phase.",
             f"- {args['repetitions']} trials per workload/client count; at least {args['requests']} requests and {args.get('seconds', 0)} measured seconds per trial. {args['warmup_rounds']} untimed rounds over all query classes plus per-phase/per-client warm-up.",
             "- Closed-loop persistent HTTP/1.1 clients on host loopback. Latency includes request sending, response reading, and client JSON decoding; it is not network-excluded engine time or an open-loop capacity test.",
             "- Cold-cache requests use distinct casing of an absent OR term. Parsing/normalization preserves the same query meaning and selectivity. Warm requests reuse prewarmed keys. Mixed phases rotate cold groups across query classes, targeting 75% reuse.",
             "- Cache regimes are checked against actual server hit/miss counter deltas. Every response is checked for total count, top-20 page size, and a stable index generation. Any error or cache mismatch invalidates the stage.",
             "- Percentiles below pool successful raw requests across trials and use nearest rank, `ceil(p*n)-1`. Throughput is successful requests divided by the sum of measured trial wall times. Errors are reported separately.",
             "- Resource sampling runs during the workload. Docker memory is its reported working set; heap usage comes from JVM metrics. Peaks are sampled observations, not retained-index size or allocation rates.", "",
             "## Loading and recovery", "",
             "| Documents | Loading track | Upload workers | Max observed active requests | Upload p95 ms | Searchable docs/s | Seconds to searchable / ready | Errors |",
             "| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
    notes_path = source / "run-notes.json"
    if notes_path.exists():
        notes = json.loads(notes_path.read_text())
        lines[4:4] = ["## Run provenance", "", *["- " + note for note in notes], ""]
    for directory in directories:
        run = json.loads((directory / "summary.json").read_text())
        load = run.get("load", {})
        if load.get("mode") == "real-http-upload-and-kafka-indexing":
            lines.append(f"| {run['documents']:,} | Real upload + Kafka + indexing | {load['concurrent_workers']} | {load['observed_max_active_requests']} | {number(load['latency_ms']['p95'])} | {number(load['documents_per_second_to_searchable'])} | {number(load['seconds_to_all_searchable'])} | {load['errors']} |")
        else:
            lines.append(f"| {run['documents']:,} | Token-projection fixture + startup recovery | n/a | n/a | n/a | n/a | {number(load.get('restart_to_ready_seconds'))} | {'0' if run.get('valid') else 'invalid'} |")
    for directory in directories:
        load = json.loads((directory / "summary.json").read_text()).get("load", {})
        if "availability" in load:
            probe = load["availability"]
            lines.extend(["", f"During the {int(directory.name):,}-document ingest track, {probe['successful']}/{probe['requests']} existing-document probes passed; probe p95 was {number(probe['latency_ms']['p95'])} ms, with {probe['errors']} errors."])
    lines.extend(["", "Each loading track was executed once; the repeated trials below apply to search workloads. The real ingest track excludes one bootstrap document and measures the remaining accepted documents until the full corpus is visible in search. Upload latency includes transfer time for these small files. Concurrent once-per-second probes verify that the bootstrap document remains searchable at sampled instants. Fixture tracks bypass upload, metadata persistence, Kafka, extraction, tokenization, and incremental per-document index construction; their ready time includes Compose dependency checks, JVM startup, and reconstruction. They establish search behavior at that index size, not end-to-end ingest throughput.", "",
                  "## Search HTTP results", "",
                  "| Documents | Cache regime | Clients | Requests | p50 ms | p95 ms | p99 ms | Requests/s | Measured hit rate | Errors |",
                  "| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"])
    for directory in directories:
        run = json.loads((directory / "summary.json").read_text())
        for workers in args["concurrency"]:
            for mode in ("cold", "warm", "mixed"):
                phases = [phase for phase in run.get("phases", []) if phase["phase"] == mode and phase["workers"] == workers]
                if not phases:
                    continue
                samples = []
                for phase in phases:
                    with gzip.open(directory / f"{mode}-c{workers}-r{phase['trial']}.jsonl.gz", "rt") as stream:
                        samples.extend(json.loads(line) for line in stream)
                stats = summarize(samples, sum(phase["wall_seconds"] for phase in phases))
                hits = sum(phase["cache"]["hits"] for phase in phases)
                misses = sum(phase["cache"]["misses"] for phase in phases)
                latency = stats["latency_ms"]
                lines.append(f"| {run['documents']:,} | {mode} | {workers} | {stats['requests']} | {number(latency['p50'])} | {number(latency['p95'])} | {number(latency['p99'])} | {number(stats['successful_requests_per_second'])} | {hits / (hits + misses):.1%} | {stats['errors']} |")
        if not run.get("valid"):
            lines.extend(["", f"**Stage {run['documents']:,} is invalid/incomplete:** {run.get('error', 'not all phases completed')}.", ""])
    lines.extend(["", "## Uncached query breakdown, one client", "",
                  "Successful requests pooled across cold-cache trials. This separates expensive broad queries from narrow lookups; it uses the same raw requests as the aggregate table.", "",
                  "| Documents | Query class | Requests | p50 ms | p95 ms | p99 ms | Errors |",
                  "| ---: | --- | ---: | ---: | ---: | ---: | ---: |"])
    for directory in directories:
        by_case = {}
        for raw in sorted(directory.glob("cold-c1-r*.jsonl.gz")):
            with gzip.open(raw, "rt") as stream:
                for line in stream:
                    sample = json.loads(line)
                    by_case.setdefault(sample["case"], []).append(sample)
        for case, samples in by_case.items():
            stats = summarize(samples, 0)
            latency = stats["latency_ms"]
            lines.append(f"| {int(directory.name):,} | {case} | {stats['requests']} | {number(latency['p50'])} | {number(latency['p95'])} | {number(latency['p99'])} | {stats['errors']} |")
    lines.extend(["", "## Sampled search-process resources", "",
                  "| Documents | Heap peak MiB | Docker working-set peak MiB | Docker CPU peak (100% = one core) | Samples | Telemetry errors |",
                  "| ---: | ---: | ---: | ---: | ---: | ---: |"])
    for directory in directories:
        if (directory / "resources.json").exists():
            resource = resource_summary(directory)
            lines.append(f"| {int(directory.name):,} | {number(resource['heap_peak_mib'])} | {number(resource['search_working_set_peak_mib'])} | {number(resource['search_cpu_peak_percent_one_core_units'])} | {resource['samples']} | {resource['telemetry_errors']} |")
    lines.extend(["", "Telemetry gaps can occur while the search container is intentionally stopped/restarted or before a GC-pause meter exists; they are retained in resources.json. See timestamps and phases when interpreting peaks.", "",
                  "## Scope of the evidence", "",
                  "These results cover one search instance, a synthetic short-TXT corpus, local plaintext HTTP, and the stated client workloads. They do not establish distributed scaling, natural-language relevance quality, PDF or 10 MiB upload performance, production availability, or the specification's network-excluded latency SLO. Warm cache numbers primarily measure the HTTP/authentication/cache path. The load generator shares the host and may limit warm-cache throughput. p99 values from short trials are exploratory tails, not production guarantees.", "",
                  "## Reproduce", "", "```shell",
                  "./mvnw -Pbenchmarks -pl benchmarks/jmh -am -DskipTests package",
                  "python3 -u benchmarks/run.py --sizes 1000 10000 100000 --requests 256 --repetitions 3 --concurrency 1 16",
                  "python3 benchmarks/report.py benchmarks/output/<run-directory> --output benchmarks/output/<run-directory>/REPORT.md",
                  "```", "", "Each run uses fresh minsearch-bench-* containers and named volumes; it removes only those disposable resources afterwards. It never uses the development database. Generated corpora and materialization files remain in benchmarks/output, which is ignored by Git.", ""])
    destination.write_text("\n".join(lines))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    generate_report(args.source, args.output)
