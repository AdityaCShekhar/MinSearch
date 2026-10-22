#!/usr/bin/env python3
"""Run JMH with allocation profiling and retain source, runtime, and artifact identity."""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import platform
import subprocess

from run import ROOT, source_identity, write_json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("jmh_arguments", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    artifact = ROOT / "benchmarks/jmh/target/benchmarks.jar"
    extra = args.jmh_arguments
    if extra[:1] == ["--"]:
        extra = extra[1:]
    command = ["java", "-jar", str(artifact), *extra, "-prof", "gc", "-rf", "json",
               "-rff", str(args.output.resolve() / "jmh.json")]
    metadata = {**source_identity(), "utc": datetime.now(timezone.utc).isoformat(),
                "artifact_sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
                "java": subprocess.run(["java", "-version"], capture_output=True, text=True, check=True).stderr,
                "platform": platform.platform(), "architecture": platform.machine(),
                "command": command}
    write_json(args.output / "environment.json", metadata)
    print("Running JMH; log:", args.output / "jmh.log", flush=True)
    with (args.output / "jmh.log").open("w") as log:
        subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True, cwd=ROOT)
    measurements = json.loads((args.output / "jmh.json").read_text())
    if not measurements or "<failure>" in (args.output / "jmh.log").read_text():
        raise RuntimeError("JMH failed; inspect the retained log")
    print("Completed:", len(measurements), "benchmark/parameter combinations", flush=True)


if __name__ == "__main__":
    main()
