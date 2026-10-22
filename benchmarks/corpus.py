#!/usr/bin/env python3
"""Deterministic synthetic TXT corpus. IDs and credentials are not part of corpus content."""

import argparse
import hashlib
import json
from pathlib import Path
import random

VERSION = 1
TOPICS = "astronomy biology chemistry computing design ecology finance geology history imaging justice kinetics linguistics medicine networks optics".split()
VOCABULARY = "algorithm analysis archive boundary buffer cache capacity channel cluster collection commit concurrency connection consistency content context database delivery dependency document durable engine event execution expression failure field filter generation graph history immutable index information iteration language latency memory metadata network normalization operation ordering owner partition persistence position prefix process publication query ranking record recovery reference request response retry revision search sequence snapshot storage stream system table term throughput token transaction validation value version vocabulary worker".split()
PHRASES = ("distributed systems", "relational databases", "transactional messaging", "document retrieval")
MISSING = "nonexistentbenchmarkmarker"


def document(index, seed=20261019, tokens=64):
    if index < 0 or tokens < 8:
        raise ValueError("Index must be nonnegative and token count must be at least 8")
    rng = random.Random((seed << 32) + index)
    length = tokens * (4 if index % 20 == 0 else 2 if index % 20 < 4 else 1)
    words = [f"benchmarkdocument{index:08d}", TOPICS[index % len(TOPICS)], "benchmark", "archive"]
    words.extend(PHRASES[index % len(PHRASES)].split())
    words.extend(VOCABULARY[rng.getrandbits(32) % len(VOCABULARY)] for _ in range(length - len(words)))
    text = " ".join(words) + "\n"
    return {"index": index, "filename": f"document-{index:08d}.txt", "tokens": length,
            "text": text, "sha256": hashlib.sha256(text.encode()).hexdigest()}


def query_cases(count):
    if count < 1:
        raise ValueError("Corpus must contain documents")
    return [
        {"name": "rare_term", "query": f"benchmarkdocument{count - 1:08d}", "expected_total": 1, "expected_index": count - 1},
        {"name": "boolean", "query": "astronomy AND benchmark", "expected_total": (count + 15) // 16},
        {"name": "phrase", "query": '"distributed systems"', "expected_total": (count + 3) // 4},
        {"name": "filter", "query": "biology type:txt", "expected_total": (count + 14) // 16},
        {"name": "prefix", "query": "benchmarkdocument*", "expected_total": min(100, count)},
        {"name": "fuzzy", "query": "astronomx~", "expected_total": (count + 15) // 16},
        {"name": "negative", "query": "benchmark NOT optics", "expected_total": count - count // 16},
        {"name": "missing", "query": "neverindexedterm", "expected_total": 0},
    ]


def measured_query(base, variant=0):
    # Distinct cache keys with the same parsed meaning, including query length and selectivity.
    if variant < 0 or variant >= 2 ** len(MISSING):
        raise ValueError("Cache-key variant exceeds the distinct case-variant space")
    suffix = "".join(char.upper() if variant & (1 << bit) else char for bit, char in enumerate(MISSING))
    return f"({base}) OR {suffix}"


def generate(output, count=100000, seed=20261019, tokens=64):
    output.mkdir(parents=True, exist_ok=True)
    destination = output / "corpus.jsonl"
    digest = hashlib.sha256()
    sizes, token_counts = [], []
    with destination.open("wb") as stream:
        for index in range(count):
            item = document(index, seed, tokens)
            line = (json.dumps(item, sort_keys=True, separators=(",", ":")) + "\n").encode()
            stream.write(line)
            digest.update(line)
            sizes.append(len(item["text"].encode()))
            token_counts.append(item["tokens"])
    manifest = {"schema_version": VERSION, "kind": "synthetic-short-ascii-txt", "documents": count,
                "seed": seed, "base_tokens": tokens, "topics": TOPICS, "vocabulary": VOCABULARY,
                "sha256": digest.hexdigest(), "source_bytes": sum(sizes),
                "bytes_min": min(sizes), "bytes_max": max(sizes), "bytes_mean": sum(sizes) / count,
                "tokens_total": sum(token_counts), "tokens_mean": sum(token_counts) / count,
                "queries": query_cases(count), "generator_version": VERSION}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output/corpus")
    parser.add_argument("--documents", type=int, default=100000)
    parser.add_argument("--seed", type=int, default=20261019)
    parser.add_argument("--tokens", type=int, default=64)
    args = parser.parse_args()
    if args.documents < 1:
        parser.error("--documents must be positive")
    print(json.dumps(generate(args.output, args.documents, args.seed, args.tokens), indent=2))
