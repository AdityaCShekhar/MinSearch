#!/usr/bin/env python3
"""Verify Kafka failure recovery and exact-offset replay in a disposable Compose stack."""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import uuid

from corpus import document
from run import Api, PORTS, Stack, eventually, multipart, shell_output, source_identity, write_json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--skip-build", action="store_true")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    stack = Stack("minsearch-bench-delivery-" + uuid.uuid4().hex[:8], args.output)
    result = {**source_identity(), "utc": datetime.now(timezone.utc).isoformat(), "checks": [], "valid": False}
    started = False
    try:
        if not args.skip_build:
            with (args.output / "build.log").open("w") as log:
                stack.call("build", stdout=log, stderr=subprocess.STDOUT)
        if shell_output([*stack.command, "ps", "--all", "--quiet"]):
            raise RuntimeError("Refusing to reuse an existing project")
        started = True
        with (args.output / "startup.log").open("w") as log:
            stack.call("up", "--detach", "--no-build", stdout=log, stderr=subprocess.STDOUT)
        api = Api()
        for service in PORTS:
            eventually(lambda service=service: api.request(service, "GET", "/actuator/health/readiness", authenticated=False)[0]["status"] == "UP", service + " readiness", 180)
        credentials = {"email": "delivery-" + uuid.uuid4().hex + "@example.com", "password": uuid.uuid4().hex}
        api.request("auth", "POST", "/api/v1/auth/register", payload=credentials, authenticated=False)
        api.credentials = credentials
        api.refresh()

        def upload(index):
            item = document(index, 20261019, 64)
            body, content_type = multipart(item)
            return api.request("document", "POST", "/api/v1/documents", body=body, content_type=content_type)[0]

        def wait_status(doc, expected):
            return eventually(lambda: api.request("document", "GET", f'/api/v1/documents/{doc["id"]}/status')[0]["status"] == expected, expected, 90)

        def replay(topic):
            offsets = shell_output([*stack.command, "exec", "-T", "kafka", "/opt/kafka/bin/kafka-get-offsets.sh", "--bootstrap-server", "kafka:29092", "--topic", topic])
            nonempty = [line.rsplit(":", 2) for line in offsets.splitlines() if line.startswith(topic + ":") and int(line.rsplit(":", 1)[1]) > 0]
            if len(nonempty) != 1:
                raise AssertionError(f"Expected exactly one nonempty partition in {topic}: {offsets}")
            _, partition, end = nonempty[0]
            command = [*stack.command, "exec", "-T", "search-service", "java",
                       "-Dloader.main=com.aditya.minsearch.search.infrastructure.DeadLetterReplay",
                       "-cp", "/app/app.jar", "org.springframework.boot.loader.launch.PropertiesLauncher",
                       "--bootstrap-server", "kafka:29092", "--topic", topic,
                       "--partition", partition, "--offset", str(int(end) - 1)]
            receipts = []
            for extra in ([], ["--execute"], ["--execute"]):
                run = subprocess.run(command + extra, check=True, capture_output=True, text=True)
                with (args.output / "replay.log").open("a") as log:
                    log.write(run.stdout + run.stderr)
                receipts.append(next(json.loads(line) for line in run.stdout.splitlines()
                                     if line.startswith("{") and '"sourceTopic"' in line))
            result["checks"].append({"topic": topic, "receipts": receipts})

        # A genuine terminal extraction failure, followed by restoration and duplicate replay.
        stack.call("stop", "search-service", stdout=subprocess.DEVNULL)
        first = upload(0)
        key = "/data/documents/" + first["storageKey"]
        stack.call("exec", "-T", "document-service", "mv", key, key + ".held")
        stack.call("start", "search-service", stdout=subprocess.DEVNULL)
        wait_status(first, "INDEX_FAILED")
        stack.call("exec", "-T", "document-service", "mv", key + ".held", key)
        replay("document.uploaded.DLT")
        wait_status(first, "INDEXED")
        found = eventually(lambda: api.request("search", "POST", "/api/v1/search", payload={"query": "benchmarkdocument00000000"})[0], "search after intentional restart", 30)
        assert found["total"] == 1
        assert stack.sql("search", f"SELECT count(*) FROM index_outbox WHERE document_id='{first['id']}' AND event_type='index.completed'") == "1"
        result["checks"].append({"check": "terminal failure repaired; duplicate replay creates one completion", "passed": True})

        # Force a transactional outcome-consumer failure, then repair and replay its DLT record.
        stack.sql("document", "ALTER TABLE processed_events ADD CONSTRAINT reject_delivery_test CHECK (false) NOT VALID")
        second = upload(1)
        eventually(lambda: "index.completed.DLT" in shell_output([*stack.command, "exec", "-T", "kafka", "/opt/kafka/bin/kafka-get-offsets.sh", "--bootstrap-server", "kafka:29092", "--topic", "index.completed.DLT"]) and stack.sql("document", f"SELECT status FROM documents WHERE id='{second['id']}'") == "UPLOADED", "outcome awaiting recovery", 30)
        # Offsets become nonzero only once bounded retries have actually dead-lettered the record.
        eventually(lambda: any(int(line.rsplit(":", 1)[1]) > 0 for line in shell_output([*stack.command, "exec", "-T", "kafka", "/opt/kafka/bin/kafka-get-offsets.sh", "--bootstrap-server", "kafka:29092", "--topic", "index.completed.DLT"]).splitlines() if line.startswith("index.completed.DLT:")), "outcome dead-letter delivery", 60)
        stack.sql("document", "ALTER TABLE processed_events DROP CONSTRAINT reject_delivery_test")
        replay("index.completed.DLT")
        wait_status(second, "INDEXED")
        result["checks"].append({"check": "outcome transaction recovered; duplicate replay is idempotent", "passed": True})

        stack.call("stop", "kafka", stdout=subprocess.DEVNULL)
        third = upload(2)
        eventually(lambda: int(stack.sql("document", f"SELECT attempts FROM document_outbox WHERE aggregate_id='{third['id']}'")) > 0, "outbox retry after broker failure", 60)
        stack.call("start", "kafka", stdout=subprocess.DEVNULL)
        wait_status(third, "INDEXED")
        result["checks"].append({"check": "outbox retries an unavailable broker and recovers", "passed": True})
        result["valid"] = True
        print("PASS: document and outcome replay, duplicate safety, and broker recovery", flush=True)
    except BaseException as error:
        result["error"] = str(error)
        raise
    finally:
        try:
            if started:
                stack.capture()
        finally:
            write_json(args.output / "validation.json", result)
            if started:
                with (args.output / "cleanup.log").open("w") as log:
                    stack.call("down", "--volumes", stdout=log, stderr=subprocess.STDOUT)


if __name__ == "__main__":
    main()
