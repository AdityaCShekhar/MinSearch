#!/usr/bin/env python3
"""Run the bounded engine workload and summarize CPU, allocation, and monitor JFR events."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import platform
import re
import subprocess

from run import ROOT, source_identity, write_json


def summarize(events):
    counts, cpu, allocation, monitors = Counter(), Counter(), Counter(), Counter()
    waits = []
    for event in events:
        kind, values = event["type"], event["values"]
        counts[kind] += 1
        frames = (values.get("stackTrace") or {}).get("frames", [])
        methods = [frame["method"]["type"]["name"].replace("/", ".") + "." + frame["method"]["name"] for frame in frames]
        application = next((method for method in methods if method.startswith("com.aditya.minsearch.")), "<runtime>")
        if kind == "jdk.ExecutionSample":
            cpu[application] += 1
        elif kind == "jdk.ObjectAllocationSample":
            allocation[application] += values["weight"]
        elif kind == "jdk.JavaMonitorEnter":
            monitors[values["monitorClass"]["name"].replace("/", ".")] += 1
            if "duration" in values:
                duration = re.fullmatch(r"PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?", values["duration"])
                if not duration:
                    raise ValueError("Unsupported JFR duration: " + values["duration"])
                hours, minutes, seconds = (float(value or 0) for value in duration.groups())
                waits.append((hours * 3600 + minutes * 60 + seconds) * 1000)
    waits.sort()
    return {"event_counts": dict(counts), "cpu_application_frames": cpu.most_common(20),
            "allocation_sample_weight_by_application_frame": allocation.most_common(20),
            "contended_monitors_over_1ms": monitors.most_common(20),
            "recorded_monitor_wait_ms": {"total": sum(waits), "max": max(waits, default=0),
                "p50": waits[(len(waits) - 1) // 2] if waits else None,
                "p95": waits[max(0, math.ceil(len(waits) * .95) - 1)] if waits else None},
            "note": "JFR CPU counts and allocation weights are samples, not exact method time or allocated bytes. Monitor events omit waits below 1 ms."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--artifact", type=Path, default=ROOT / "benchmarks/jmh/target/benchmarks.jar")
    parser.add_argument("--documents", type=int, default=10000)
    parser.add_argument("--seconds", type=int, default=20)
    args = parser.parse_args()
    if args.documents < 1 or args.seconds < 1:
        parser.error("Documents and seconds must be positive")
    args.output.mkdir(parents=True, exist_ok=False)
    runtime = subprocess.run(["java", "-XshowSettings:properties", "-version"], capture_output=True, text=True, check=True).stderr
    java_home = re.search(r"java.home = (.+)", runtime).group(1).strip()
    command = ["java", "-Xms512m", "-Xmx2g", "-cp", str(args.artifact.resolve()),
               "com.aditya.minsearch.benchmark.ProfileWorkload", str(args.documents), str(args.seconds), str(args.output.resolve())]
    write_json(args.output / "environment.json", {**source_identity(), "utc": datetime.now(timezone.utc).isoformat(),
               "artifact_sha256": hashlib.sha256(args.artifact.read_bytes()).hexdigest(), "java": runtime,
               "platform": platform.platform(), "command": command})
    with (args.output / "profile.log").open("w") as log:
        subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True, cwd=ROOT)
    raw = subprocess.check_output([str(Path(java_home) / "bin/jfr"), "print", "--json", "--events",
                                   "jdk.ExecutionSample,jdk.ObjectAllocationSample,jdk.JavaMonitorEnter,jdk.ThreadPark,jdk.GarbageCollection",
                                   str(args.output / "profile.jfr")])
    write_json(args.output / "events-summary.json", summarize(json.loads(raw)["recording"]["events"]))
    print((args.output / "summary.json").read_text(), flush=True)


if __name__ == "__main__":
    main()
