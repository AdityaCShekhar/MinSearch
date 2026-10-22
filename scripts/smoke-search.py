#!/usr/bin/env python3
"""Exercise the running auth -> upload -> Kafka -> index -> search lifecycle."""

import argparse
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid


def request(base, path, method="GET", token=None, payload=None, body=None, headers=None, expected_error=None):
    outgoing = dict(headers or {})
    if token:
        outgoing["Authorization"] = f"Bearer {token}"
    if payload is not None:
        body = json.dumps(payload).encode()
        outgoing["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, body, outgoing, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            content = response.read()
            return response.status, json.loads(content) if content else None
    except urllib.error.HTTPError as error:
        if error.code == expected_error:
            return error.code, None
        raise AssertionError(f"{method} {path}: HTTP {error.code}: {error.read().decode()}") from error


def multipart(fields, file=None):
    boundary = "minsearch-" + uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    if file:
        filename, content = file
        media_type = "application/pdf" if filename.endswith(".pdf") else "text/plain"
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: {media_type}\r\n\r\n'.encode() + content + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), {"Content-Type": f"multipart/form-data; boundary={boundary}"}


def eventually(check, description, timeout):
    deadline = time.monotonic() + timeout
    while True:
        result = check()
        if result:
            return result
        if time.monotonic() >= deadline:
            raise AssertionError(f"Timed out waiting for {description}")
        time.sleep(0.25)


def wait_for_service(base, timeout):
    def ready():
        try:
            return request(base, "/actuator/health/readiness")[1]["status"] == "UP"
        except (urllib.error.URLError, AssertionError, TimeoutError, ConnectionError):
            return False
    eventually(ready, f"{base} to become ready", timeout)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--auth-url", default="http://localhost:8081")
    parser.add_argument("--document-url", default="http://localhost:8082")
    parser.add_argument("--search-url", default="http://localhost:8083")
    parser.add_argument("--timeout", type=float, default=30)
    parser.add_argument("--restart-search", action="store_true", help="Restart the local Compose search service and verify index recovery")
    parser.add_argument("--verify-cleanup", action="store_true", help="Check that source files were removed from the local Compose document volume")
    args = parser.parse_args()
    for base in (args.auth_url, args.document_url, args.search_url):
        wait_for_service(base, args.timeout)

    def account():
        credentials = {"email": f"smoke-{uuid.uuid4().hex}@example.com", "password": uuid.uuid4().hex}
        status, user = request(args.auth_url, "/api/v1/auth/register", "POST", payload=credentials)
        assert status == 201
        _, tokens = request(args.auth_url, "/api/v1/auth/login", "POST", payload=credentials)
        return user["id"], tokens["accessToken"]

    owner, token = account()
    _, other_token = account()
    marker = "smoke" + uuid.uuid4().hex
    body, headers = multipart({"language": "en"}, ("smoke.txt", f"distributed systems {marker}".encode()))
    status, document = request(args.document_url, "/api/v1/documents", "POST", token, body=body, headers=headers)
    assert status == 202
    document_id = document["id"]

    def processing_status(identifier):
        return request(args.document_url, f"/api/v1/documents/{identifier}/status", token=token)[1]

    def wait_status(identifier, version, expected):
        def matches():
            status = processing_status(identifier)
            return status if status["version"] == version and status["status"] == expected else None
        return eventually(matches, f"document {identifier} version {version} to become {expected}", args.timeout)

    def verify_removed(storage_key):
        if args.verify_cleanup:
            compose = Path(__file__).resolve().parents[1] / "infrastructure/docker/docker-compose.yml"
            subprocess.run(["docker", "compose", "-f", str(compose), "exec", "-T", "document-service",
                            "test", "!", "-e", "/data/documents/" + storage_key], check=True)

    def search(query, identity=token, extra_headers=None):
        return request(args.search_url, "/api/v1/search", "POST", identity,
                       payload={"query": query}, headers=extra_headers)[1]

    query = f'"distributed systems" {marker}'
    def uploaded():
        result = search(query)
        return result if any(hit["documentId"] == document_id for hit in result["results"]) else None
    result = eventually(uploaded, "uploaded document to become searchable", args.timeout)
    wait_status(document_id, 1, "INDEXED")
    assert result["total"] == 1
    status, _ = request(args.search_url, "/api/v1/search", "POST", payload={"query": query}, expected_error=401)
    assert status == 401
    assert search(query, other_token, {"X-Owner-Id": owner})["total"] == 0
    _, completions = request(args.search_url, f"/api/v1/autocomplete?prefix={marker}", token=other_token)
    assert completions["completions"] == []
    _, suggestions = request(args.search_url, f"/api/v1/suggest?term={marker}", token=other_token)
    assert suggestions == {}
    status, _ = request(args.document_url, f"/api/v1/documents/{document_id}/status", token=other_token, expected_error=400)
    assert status == 400
    print("PASS: upload -> phrase search -> INDEXED status; owner isolation enforced")

    body, headers = multipart({"language": "fr", "title": "Updated smoke document"})
    status, updated = request(args.document_url, f"/api/v1/documents/{document_id}", "PUT", token, body=body, headers=headers)
    assert status == 202 and updated["currentVersion"] == 2
    eventually(lambda: search(f"{marker} language:fr")["total"] == 1,
               "updated language to reach the index", args.timeout)
    assert search(f"{marker} language:en")["total"] == 0
    wait_status(document_id, 2, "INDEXED")
    print("PASS: metadata update replaces the searchable version and invalidates cached results")

    if args.restart_search:
        compose = Path(__file__).resolve().parents[1] / "infrastructure/docker/docker-compose.yml"
        subprocess.run(["docker", "compose", "-f", str(compose), "restart", "search-service"], check=True)
        wait_for_service(args.search_url, args.timeout)
        assert search(f"{marker} language:fr")["total"] == 1
        print("PASS: index and owner metadata survive a search-service restart")
    status, deleted = request(args.document_url, f"/api/v1/documents/{document_id}", "DELETE", token)
    assert status == 202 and deleted["currentVersion"] == 3
    eventually(lambda: search(query)["total"] == 0, "deleted document to leave search", args.timeout)
    wait_status(document_id, 3, "DELETED")
    verify_removed(document["storageKey"])
    print("PASS: deletion removes search results and reaches DELETED after file cleanup")

    body, headers = multipart({"language": "en"}, ("invalid.pdf", b"%PDF-1.7\nMalformed body for terminal extraction test."))
    status, failed = request(args.document_url, "/api/v1/documents", "POST", token, body=body, headers=headers)
    assert status == 202
    failed_status = wait_status(failed["id"], 1, "INDEX_FAILED")
    assert failed_status["indexingError"]
    request(args.document_url, f'/api/v1/documents/{failed["id"]}', "DELETE", token)
    wait_status(failed["id"], 2, "DELETED")
    verify_removed(failed["storageKey"])
    print("PASS: terminal PDF indexing failure exposes an error and remains safely deletable")


if __name__ == "__main__":
    main()
