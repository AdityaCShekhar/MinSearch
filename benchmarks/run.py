#!/usr/bin/env python3
"""Run isolated, validated HTTP benchmarks and retain raw samples and provenance."""

import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
import csv
from datetime import datetime, timezone
import gzip
import hashlib
import http.client
import json
import math
import os
from pathlib import Path
import platform
import re
import statistics
import subprocess
import threading
import time
import uuid

from corpus import document, generate, measured_query, query_cases

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "infrastructure/docker/docker-compose.yml"
OVERRIDE = ROOT / "benchmarks/compose.override.yml"
PORTS = {"auth": 18081, "document": 18082, "search": 18083}
NAMESPACE = uuid.UUID("ea23f2a2-c5d2-4bc8-bfb4-5b8b6fc9cfba")


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, allow_nan=False) + "\n")


def percentile(values, percent):
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * percent / 100) - 1)]


def summarize(samples, elapsed):
    good = [sample["latency_ms"] for sample in samples if sample["ok"]]
    return {"requests": len(samples), "successful": len(good), "errors": len(samples) - len(good),
            "wall_seconds": elapsed, "successful_requests_per_second": len(good) / elapsed if elapsed else 0,
            "latency_ms": {"p50": percentile(good, 50), "p95": percentile(good, 95),
                           "p99": percentile(good, 99), "mean": statistics.mean(good) if good else None,
                           "min": min(good) if good else None, "max": max(good) if good else None}}


def shell_output(command, **kwargs):
    return subprocess.check_output(command, text=True, **kwargs).strip()


def source_identity():
    digest = hashlib.sha256()
    paths = sorted([*ROOT.glob("services/**/src/main/**/*.java"), *ROOT.glob("shared/src/main/**/*.java"),
                    *ROOT.glob("services/**/src/main/**/*.properties"), *ROOT.glob("services/**/src/main/**/*.sql"),
                    *ROOT.glob("services/**/pom.xml"), ROOT / "pom.xml", ROOT / "shared/pom.xml"])
    for path in paths:
        digest.update(str(path.relative_to(ROOT)).encode() + b"\0" + path.read_bytes())
    return {"git_commit": shell_output(["git", "rev-parse", "HEAD"], cwd=ROOT),
            "git_status": shell_output(["git", "status", "--short"], cwd=ROOT),
            "application_source_sha256": digest.hexdigest()}


class Api:
    def __init__(self, credentials=None, token=None):
        self.credentials = credentials
        self.token = token
        self.local = threading.local()

    def request(self, service, method, path, payload=None, body=None, content_type=None, authenticated=True):
        connections = getattr(self.local, "connections", None)
        if connections is None:
            connections = self.local.connections = {}
        if service not in connections:
            connections[service] = http.client.HTTPConnection("127.0.0.1", PORTS[service], timeout=120)
        connection = connections[service]
        headers = {}
        if authenticated and self.token:
            headers["Authorization"] = "Bearer " + self.token
        if payload is not None:
            body = json.dumps(payload, separators=(",", ":")).encode()
            content_type = "application/json"
        if content_type:
            headers["Content-Type"] = content_type
        started = time.perf_counter()
        try:
            connection.request(method, path, body=body, headers=headers)
            response = connection.getresponse()
            data = response.read()
            decoded = json.loads(data) if data else None
            elapsed = (time.perf_counter() - started) * 1000
            if response.status >= 400:
                raise RuntimeError(f"{service} {method} {path}: HTTP {response.status}: {decoded}")
            return decoded, elapsed, response.status
        except Exception:
            connection.close()
            connections.pop(service, None)
            raise

    def refresh(self):
        if not self.credentials:
            return
        if self.token:
            claims = json.loads(base64.urlsafe_b64decode(self.token.split(".")[1] + "=="))
            if claims["exp"] > time.time() + 120:
                return
        self.token = self.request("auth", "POST", "/api/v1/auth/login", payload=self.credentials,
                                  authenticated=False)[0]["accessToken"]

    def metric(self, name, tags=""):
        data = self.request("search", "GET", "/actuator/metrics/" + name + tags)[0]
        return {entry["statistic"]: entry["value"] for entry in data["measurements"]}

    def scalar(self, name, tags=""):
        return next(iter(self.metric(name, tags).values()))

    def counters(self):
        return {key: self.scalar("minsearch.search.cache." + key) for key in ("hits", "misses")}


def eventually(check, description, timeout):
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            value = check()
            if value:
                return value
        except (OSError, RuntimeError, http.client.HTTPException) as error:
            last_error = str(error)
        time.sleep(0.5)
    raise RuntimeError(f"Timed out: {description}; last error: {last_error}")


class Stack:
    def __init__(self, project, output):
        if not re.fullmatch(r"minsearch-bench-[a-z0-9-]+", project):
            raise ValueError("Disposable benchmark project must use the minsearch-bench- prefix")
        self.project = project
        self.output = output
        self.command = ["docker", "compose", "--project-name", project, "-f", str(BASE), "-f", str(OVERRIDE)]

    def call(self, *arguments, **kwargs):
        return subprocess.run([*self.command, *arguments], check=True, **kwargs)

    def sql(self, service, sql, stdin=None):
        if service not in ("auth", "document", "search"):
            raise ValueError("Unknown database owner")
        command = [*self.command, "exec", "-T", service + "-postgres", "sh", "-c",
                   'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1 -c "$1"', "benchmark", sql]
        return subprocess.run(command, stdin=stdin, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout.decode().strip()

    def capture(self):
        with (self.output / "containers.log").open("w") as log:
            subprocess.run([*self.command, "logs", "--no-color", "--tail", "1000"], stdout=log, stderr=subprocess.STDOUT)
        ids = shell_output([*self.command, "ps", "--all", "--quiet"]).splitlines()
        if ids:
            inspected = json.loads(shell_output(["docker", "inspect", *ids]))
            safe = []
            for container in inspected:
                safe.append({"name": container["Name"], "image_id": container["Image"],
                             "state": container["State"],
                             "host_config": {key: container["HostConfig"].get(key) for key in ("NanoCpus", "Memory", "MemorySwap")},
                             "jvm_options": next((item.split("=", 1)[1] for item in container["Config"]["Env"] if item.startswith("JAVA_TOOL_OPTIONS=")), None)})
            write_json(self.output / "containers.json", safe)
        runtime = subprocess.run([*self.command, "exec", "-T", "search-service", "java", "-version"],
                                 capture_output=True, text=True)
        write_json(self.output / "java-runtime.json", {"exit_code": runtime.returncode,
                   "version": runtime.stdout + runtime.stderr})


class Sampler:
    def __init__(self, stack, api):
        self.stack, self.api = stack, api
        self.samples, self.errors = [], []
        self.phase = "setup"
        self.stopping = threading.Event()
        self.thread = threading.Thread(target=self.run, daemon=True)

    def run(self):
        while not self.stopping.is_set():
            sample = {"utc": datetime.now(timezone.utc).isoformat(), "phase": self.phase}
            try:
                ids = shell_output([*self.stack.command, "ps", "--quiet", "--status", "running"]).splitlines()
                if ids:
                    lines = shell_output(["docker", "stats", "--no-stream", "--format", "{{json .}}", *ids]).splitlines()
                    sample["docker"] = [json.loads(line) for line in lines]
                sample["heap_used_bytes"] = self.api.scalar("jvm.memory.used", "?tag=area%3Aheap")
                sample["heap_max_bytes"] = self.api.scalar("jvm.memory.max", "?tag=area%3Aheap")
                sample["process_cpu_usage"] = self.api.scalar("process.cpu.usage")
                sample["jvm_gc_pause"] = self.api.metric("jvm.gc.pause")
            except Exception as error:
                self.errors.append({"utc": sample["utc"], "phase": self.phase, "error": str(error)})
            self.samples.append(sample)
            self.stopping.wait(2)

    def stop(self):
        self.stopping.set()
        self.thread.join(timeout=15)
        write_json(self.stack.output / "resources.json", {"samples": self.samples, "errors": self.errors})


def multipart(item):
    boundary = "minsearch-reproducible-benchmark"
    header = (f'--{boundary}\r\nContent-Disposition: form-data; name="language"\r\n\r\nen\r\n'
              f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{item["filename"]}"\r\n'
              'Content-Type: text/plain\r\n\r\n').encode()
    return header + item["text"].encode() + f"\r\n--{boundary}--\r\n".encode(), "multipart/form-data; boundary=" + boundary


def upload_track(stack, api, count, args, sampler):
    prepared = [document(index, args.seed, args.tokens) for index in range(count)]
    mapping = {}
    first, content_type = multipart(prepared[0])
    first_document = api.request("document", "POST", "/api/v1/documents", body=first, content_type=content_type)[0]
    mapping[0] = first_document["id"]
    eventually(lambda: api.scalar("minsearch.index.documents") == 1, "bootstrap document indexing", args.index_timeout)
    # Bootstrap and JVM startup are excluded from the sustained ingest interval.
    barrier = threading.Barrier(min(args.upload_concurrency, count - 1)) if count > 1 else None
    active, maximum_active = 0, 0
    lock = threading.Lock()
    probe_stop = threading.Event()
    probes = []
    samples = []

    def probe():
        client = Api(token=api.token)
        while not probe_stop.is_set():
            began = time.perf_counter()
            try:
                result, latency, status = client.request("search", "POST", "/api/v1/search", payload={"query": "benchmarkdocument00000000"})
                if result["total"] != 1:
                    raise RuntimeError("Existing document disappeared during indexing")
                probes.append({"ok": True, "latency_ms": latency, "status": status})
            except Exception as error:
                probes.append({"ok": False, "latency_ms": (time.perf_counter() - began) * 1000, "error": str(error)})
            probe_stop.wait(1)

    def upload(index):
        nonlocal active, maximum_active
        body, media_type = multipart(prepared[index])
        if index <= min(args.upload_concurrency, count - 1):
            barrier.wait(timeout=60)
        began = time.perf_counter()
        with lock:
            active += 1
            maximum_active = max(maximum_active, active)
        try:
            result, latency, status = api.request("document", "POST", "/api/v1/documents", body=body, content_type=media_type)
            if status != 202:
                raise RuntimeError("Upload did not return 202")
            with lock:
                mapping[index] = result["id"]
            return {"index": index, "ok": True, "latency_ms": latency, "status": status, "bytes": len(prepared[index]["text"].encode())}
        except Exception as error:
            return {"index": index, "ok": False, "latency_ms": (time.perf_counter() - began) * 1000, "error": str(error)}
        finally:
            with lock:
                active -= 1

    sampler.phase = "upload"
    probe_thread = threading.Thread(target=probe, daemon=True)
    probe_thread.start()
    started = time.perf_counter()
    try:
        with ThreadPoolExecutor(max_workers=args.upload_concurrency) as pool:
            samples = list(pool.map(upload, range(1, count)))
        accepted_elapsed = time.perf_counter() - started
        if any(not sample["ok"] for sample in samples):
            raise RuntimeError("Upload requests failed; see raw samples")
        eventually(lambda: api.scalar("minsearch.index.documents") == count, "all accepted documents becoming searchable", args.index_timeout)
        indexed_elapsed = time.perf_counter() - started
    finally:
        probe_stop.set()
        probe_thread.join(timeout=125)
        write_json(stack.output / "upload-samples.json", samples)
        write_json(stack.output / "availability-samples.json", probes)
    if any(not sample["ok"] for sample in probes):
        raise RuntimeError("Search failed during indexing")
    for index in sorted(mapping):
        state = api.request("document", "GET", f'/api/v1/documents/{mapping[index]}/status')[0]
        if state["status"] == "INDEX_FAILED":
            raise RuntimeError(f"Document {index} failed indexing: {state['indexingError']}")
    result = summarize(samples, accepted_elapsed)
    result.update({"mode": "real-http-upload-and-kafka-indexing", "bootstrap_documents_excluded": 1,
                   "concurrent_workers": args.upload_concurrency, "observed_max_active_requests": maximum_active,
                   "indexed_documents": count - 1, "seconds_to_all_searchable": indexed_elapsed,
                   "documents_per_second_to_searchable": (count - 1) / indexed_elapsed,
                   "source_bytes": sum(len(item["text"].encode()) for item in prepared),
                   "availability": summarize(probes, indexed_elapsed)})
    write_json(stack.output / "document-map.json", mapping)
    return result


def seed_track(stack, api, owner, count, args, sampler):
    sampler.phase = "fixture-materialization"
    stack.call("stop", "search-service", stdout=subprocess.DEVNULL)
    destination = stack.output / "projection.tsv"
    digest = hashlib.sha256()
    with destination.open("w", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        for index in range(count):
            item = document(index, args.seed, args.tokens)
            digest.update((json.dumps(item, sort_keys=True, separators=(",", ":")) + "\n").encode())
            doc_id = str(uuid.uuid5(NAMESPACE, f"{args.seed}:{index}"))
            offset, tokens = 0, []
            for position, term in enumerate(item["text"].split()):
                tokens.append({"term": term, "position": position, "startOffset": offset, "endOffset": offset + len(term)})
                offset += len(term) + 1
            mutation = {"eventId": str(uuid.uuid5(NAMESPACE, f"event:{args.seed}:{index}")), "documentId": doc_id,
                        "version": 1, "deleted": False, "document": {"id": doc_id, "ownerId": owner,
                        "language": "en", "type": "txt", "uploadedAt": "2026-01-01T00:00:00Z"}, "tokens": tokens}
            writer.writerow([doc_id, 1, index + 1, json.dumps(mutation, separators=(",", ":"))])
    with destination.open("rb") as stream:
        stack.sql("search", "COPY search_index_documents(document_id,document_version,revision,mutation) FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t')", stdin=stream)
    stack.sql("search", f"SELECT setval('search_index_revision',{count},true)")
    began = time.perf_counter()
    stack.call("start", "search-service", stdout=subprocess.DEVNULL)
    eventually(lambda: api.request("search", "GET", "/actuator/health/readiness", authenticated=False)[0]["status"] == "UP",
               "fixture-loaded search startup", args.index_timeout)
    api.refresh()
    eventually(lambda: api.scalar("minsearch.index.documents") == count, "fixture-loaded index size", args.index_timeout)
    return {"mode": "direct-token-projection-fixture", "documents": count, "corpus_prefix_sha256": digest.hexdigest(),
            "restart_to_ready_seconds": time.perf_counter() - began,
            "excludes": ["upload", "document persistence", "Kafka delivery", "extraction", "tokenization", "per-document incremental indexing"]}


def search_phase(stack, api, count, args, sampler, mode, workers, trial):
    api.refresh()
    cases = query_cases(count)
    phase_number = trial * len(args.concurrency) * 3 + args.concurrency.index(workers) * 3 + ("cold", "warm", "mixed").index(mode)
    variant_stride = (2 ** len("nonexistentbenchmarkmarker")) // (args.repetitions * len(args.concurrency) * 3 + 1)
    # A read of an old hot key does not refresh its expire-after-write TTL. Give each
    # phase a new hot-key namespace so entries cannot expire halfway through a warm trial.
    hot_variant = 1 + phase_number * variant_stride
    for case in cases:
        found = api.request("search", "POST", "/api/v1/search", payload={"query": measured_query(case["query"], hot_variant), "size": 20})[0]
        if found["total"] != case["expected_total"]:
            raise RuntimeError("Phase warm-up returned an incorrect result count")
    barrier = threading.Barrier(workers + 1)
    gate = threading.Event()
    samples = []
    assignment = threading.Lock()
    sequence = 0
    deadline = [0.0]
    sampler.phase = f"{mode}-c{workers}-r{trial}"

    def worker(number):
        nonlocal sequence
        client = Api(token=api.token)
        for step in range(2):
            case = cases[(number + step) % len(cases)]
            client.request("search", "POST", "/api/v1/search", payload={"query": measured_query(case["query"], hot_variant), "size": 20})
        barrier.wait(timeout=120)
        gate.wait(timeout=120)
        local = []
        while True:
            with assignment:
                if sequence >= args.requests and time.perf_counter() >= deadline[0]:
                    break
                index = sequence
                sequence += 1
            case = cases[index % len(cases)]
            cold = mode == "cold" or (mode == "mixed" and (index // len(cases) + trial) % 4 == 0)
            if index >= variant_stride - 1:
                raise RuntimeError("Phase exhausted its distinct cold-cache key space")
            variant = hot_variant + 1 + index if cold else hot_variant
            query = measured_query(case["query"], variant)
            began = time.perf_counter()
            sample = {"phase": mode, "trial": trial, "workers": workers, "index": index, "case": case["name"], "intended_cache": "miss" if cold else "hit"}
            try:
                result, latency, status = client.request("search", "POST", "/api/v1/search", payload={"query": query, "size": 20})
                if result["total"] != case["expected_total"]:
                    raise RuntimeError(f"Incorrect result count: {result['total']} != {case['expected_total']}")
                if len(result["results"]) != min(20, case["expected_total"]):
                    raise RuntimeError("Incorrect top-20 result page")
                sample.update(ok=True, latency_ms=latency, status=status, total=result["total"], generation=result["generation"])
            except Exception as error:
                sample.update(ok=False, latency_ms=(time.perf_counter() - began) * 1000, error=str(error))
            local.append(sample)
        return local

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(worker, number) for number in range(workers)]
        barrier.wait(timeout=120)
        before = api.counters()
        generation = api.scalar("minsearch.index.generation")
        started = time.perf_counter()
        deadline[0] = started + args.seconds
        gate.set()
        for future in futures:
            samples.extend(future.result())
        elapsed = time.perf_counter() - started
    after = api.counters()
    samples.sort(key=lambda item: item["index"])
    with gzip.open(stack.output / f"{mode}-c{workers}-r{trial}.jsonl.gz", "wt") as stream:
        for sample in samples:
            stream.write(json.dumps(sample, separators=(",", ":")) + "\n")
    result = summarize(samples, elapsed)
    deltas = {key: after[key] - before[key] for key in before}
    lookups = sum(deltas.values())
    result.update(phase=mode, workers=workers, trial=trial, cache=deltas,
                  cache_hit_rate=deltas["hits"] / lookups if lookups else None,
                  by_query={case["name"]: summarize([sample for sample in samples if sample["case"] == case["name"]], elapsed) for case in cases})
    if result["errors"] or any(sample.get("generation") != generation for sample in samples):
        raise RuntimeError("Invalid search results or changing generation; see raw samples")
    expected_hits = sum(sample["intended_cache"] == "hit" for sample in samples)
    if deltas != {"hits": expected_hits, "misses": len(samples) - expected_hits}:
        raise RuntimeError(f"Cache state was not controlled: {deltas}, expected {expected_hits} hits")
    return result


def run_size(count, args, output, suffix):
    directory = output / str(count)
    directory.mkdir()
    stack = Stack(f"minsearch-bench-{suffix}-{count}", directory)
    result = {"documents": count, "valid": False, "phases": []}
    api = Api()
    sampler = None
    started = False
    try:
        if shell_output([*stack.command, "ps", "--all", "--quiet"]):
            raise RuntimeError("Refusing to reuse an existing benchmark project")
        started = True
        with (directory / "startup.log").open("w") as log:
            stack.call("up", "--detach", "--no-build", stdout=log, stderr=subprocess.STDOUT)
        for service in PORTS:
            eventually(lambda service=service: api.request(service, "GET", "/actuator/health/readiness", authenticated=False)[0]["status"] == "UP",
                       service + " readiness", args.index_timeout)
        credentials = {"email": f"benchmark-{suffix}-{count}@example.com", "password": uuid.uuid4().hex}
        owner = api.request("auth", "POST", "/api/v1/auth/register", payload=credentials, authenticated=False)[0]["id"]
        stack.sql("auth", "UPDATE users SET role = 'ADMIN' WHERE id = '" + str(uuid.UUID(owner)) + "'")
        api.credentials = credentials
        api.refresh()
        sampler = Sampler(stack, api)
        sampler.thread.start()
        print(f"[{count}] loading corpus", flush=True)
        if count <= args.upload_documents:
            result["load"] = upload_track(stack, api, count, args, sampler)
        else:
            result["load"] = seed_track(stack, api, owner, count, args, sampler)
        print(f"[{count}] corpus ready; running search workloads", flush=True)
        # JVM warm-up and correctness checks are excluded from all reported phases.
        api.refresh()
        for _ in range(args.warmup_rounds):
            for case in query_cases(count):
                found = api.request("search", "POST", "/api/v1/search", payload={"query": measured_query(case["query"]), "size": 20})[0]
                if found["total"] != case["expected_total"]:
                    raise RuntimeError(f"Warm-up correctness failure for {case['name']}: {found['total']}")
        for trial in range(args.repetitions):
            for workers in args.concurrency:
                for mode in ("cold", "warm", "mixed"):
                    phase = search_phase(stack, api, count, args, sampler, mode, workers, trial)
                    result["phases"].append(phase)
                    write_json(directory / "summary.json", result)
                    print(f"[{count}] {mode} c={workers} trial={trial + 1}: p95={phase['latency_ms']['p95']:.2f} ms, {phase['successful_requests_per_second']:.1f} req/s, hits={phase['cache_hit_rate']:.1%}", flush=True)
        result["valid"] = True
    except BaseException as error:
        result["error"] = str(error)
        raise
    finally:
        try:
            if sampler:
                sampler.stop()
            if started:
                stack.capture()
        finally:
            write_json(directory / "summary.json", result)
            if started and not args.keep_stack:
                with (directory / "cleanup.log").open("w") as log:
                    stack.call("down", "--volumes", stdout=log, stderr=subprocess.STDOUT)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sizes", type=int, nargs="+", default=[1000, 10000, 100000])
    parser.add_argument("--upload-documents", type=int, default=1000)
    parser.add_argument("--upload-concurrency", type=int, default=100)
    parser.add_argument("--concurrency", type=int, nargs="+", default=[1, 16])
    parser.add_argument("--requests", type=int, default=256)
    parser.add_argument("--seconds", type=float, default=5, help="Minimum measured wall time per phase, in addition to --requests")
    parser.add_argument("--repetitions", type=int, default=3)
    parser.add_argument("--warmup-rounds", type=int, default=10)
    parser.add_argument("--index-timeout", type=float, default=1800)
    parser.add_argument("--seed", type=int, default=20261019)
    parser.add_argument("--tokens", type=int, default=64)
    parser.add_argument("--output", type=Path, default=ROOT / "benchmarks/output" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"))
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--keep-stack", action="store_true")
    args = parser.parse_args()
    if min(args.sizes) < 2 or min(args.concurrency) < 1 or args.requests < max(args.concurrency) or args.repetitions < 1 or args.upload_concurrency < 1 or args.seconds < 0:
        parser.error("Invalid corpus size, concurrency, request count, or repetition count")
    if args.keep_stack and len(args.sizes) > 1:
        parser.error("--keep-stack requires a single corpus size because the stages share host ports")
    args.output.mkdir(parents=True, exist_ok=False)
    suffix = uuid.uuid4().hex[:8]
    environment = {**source_identity(), "utc": datetime.now(timezone.utc).isoformat(), "platform": platform.platform(),
                   "architecture": platform.machine(), "python": platform.python_version(), "client_logical_cpus": os.cpu_count(),
                   "docker": json.loads(shell_output(["docker", "info", "--format", '{{json .}}'])),
                   "compose": shell_output(["docker", "compose", "version", "--short"]),
                   "arguments": {key: str(value) if isinstance(value, Path) else value for key, value in vars(args).items()},
                   "load_model": "closed-loop persistent HTTP/1.1 connections; two untimed requests per client before each phase",
                   "percentile_method": "nearest rank: sorted[ceil(p*n)-1], successful requests only; errors are counted separately"}
    if platform.system() == "Darwin":
        environment["host_cpu_model"] = shell_output(["sysctl", "-n", "machdep.cpu.brand_string"])
        environment["host_memory_bytes"] = int(shell_output(["sysctl", "-n", "hw.memsize"]))
    # Keep only machine/runtime facts, not proxy URLs, registry configuration, or host identifiers.
    environment["docker"] = {key: environment["docker"][key] for key in ("ServerVersion", "NCPU", "MemTotal", "Architecture", "OperatingSystem", "KernelVersion")}
    write_json(args.output / "environment.json", environment)
    generate(args.output / "corpus", max(args.sizes), args.seed, args.tokens)
    if not args.skip_build:
        builder = Stack("minsearch-bench-build-" + suffix, args.output)
        with (args.output / "build.log").open("w") as log:
            builder.call("build", stdout=log, stderr=subprocess.STDOUT)
    results = []
    try:
        for size in args.sizes:
            results.append(run_size(size, args, args.output, suffix))
    finally:
        write_json(args.output / "summary.json", {"runs": results})
    print("Results:", args.output, flush=True)


if __name__ == "__main__":
    main()
