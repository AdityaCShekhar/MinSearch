import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from corpus import document, generate, measured_query, query_cases
from run import Stack, percentile, summarize
from report import memory_bytes
from run_profile import summarize as summarize_profile


class CorpusTests(unittest.TestCase):
    def test_generator_matches_the_version_one_golden_document(self):
        self.assertEqual(document(0, 42, 16)["sha256"], "05c2fae15de0fea416e5d8e71fcc265b3cafd4fe82bc1fe692b5e4c53e754ab0")
    def test_repeated_generation_has_identical_content_checksum_and_prefixes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = generate(root / "first", 32, 42, 16)
            second = generate(root / "second", 32, 42, 16)
            larger = generate(root / "larger", 64, 42, 16)
            first_bytes = (root / "first/corpus.jsonl").read_bytes()
            self.assertEqual(first, second)
            self.assertEqual(first["sha256"], hashlib.sha256(first_bytes).hexdigest())
            self.assertTrue((root / "larger/corpus.jsonl").read_bytes().startswith(first_bytes))
            self.assertEqual(first["documents"], 32)
            self.assertEqual(larger["documents"], 64)
            for line in first_bytes.splitlines():
                item = json.loads(line)
                self.assertEqual(item["sha256"], hashlib.sha256(item["text"].encode()).hexdigest())

    def test_oracle_counts_match_generated_content_at_uneven_corpus_boundaries(self):
        for count in (2, 15, 16, 17, 39, 100, 101):
            texts = [document(index, 42, 16)["text"] for index in range(count)]
            actual = {"rare_term": sum(f"benchmarkdocument{count - 1:08d}" in text.split() for text in texts),
                      "boolean": sum("astronomy" in text.split() and "benchmark" in text.split() for text in texts),
                      "phrase": sum("distributed systems" in text for text in texts),
                      "filter": sum("biology" in text.split() for text in texts),
                      "prefix": min(count, 100),
                      "fuzzy": sum("astronomy" in text.split() for text in texts),
                      "negative": sum("optics" not in text.split() for text in texts), "missing": 0}
            self.assertEqual(actual, {case["name"]: case["expected_total"] for case in query_cases(count)})

    def test_cache_variants_are_distinct_but_have_identical_normalized_meaning(self):
        variants = [measured_query('"distributed systems"', index) for index in range(2048)]
        self.assertEqual(len(set(variants)), len(variants))
        self.assertEqual(len({query.lower() for query in variants}), 1)
        with self.assertRaises(ValueError):
            measured_query("term", -1)


class MeasurementTests(unittest.TestCase):
    def test_profile_summary_preserves_sampling_weights_and_monitor_counts(self):
        frame = {"method": {"type": {"name": "com/aditya/minsearch/search/application/QueryEvaluator"}, "name": "evaluate"}}
        result = summarize_profile([
            {"type": "jdk.ExecutionSample", "values": {"stackTrace": {"frames": [frame]}}},
            {"type": "jdk.ObjectAllocationSample", "values": {"stackTrace": {"frames": [frame]}, "weight": 2048}},
            {"type": "jdk.JavaMonitorEnter", "values": {"monitorClass": {"name": "com/aditya/minsearch/search/application/DocumentIndexingService"}, "duration": "PT0.025S"}},
        ])
        self.assertEqual(result["cpu_application_frames"][0][1], 1)
        self.assertEqual(result["allocation_sample_weight_by_application_frame"][0][1], 2048)
        self.assertEqual(result["contended_monitors_over_1ms"][0][1], 1)
        self.assertEqual(result["recorded_monitor_wait_ms"]["p95"], 25)

    def test_memory_units_keep_decimal_and_binary_units_distinct(self):
        self.assertEqual(memory_bytes("1.5GiB"), 1.5 * 1024**3)
        self.assertEqual(memory_bytes("100MB"), 100000000)
        with self.assertRaises(ValueError):
            memory_bytes("unknown")
    def test_percentiles_use_nearest_rank_and_errors_remain_visible(self):
        self.assertEqual(percentile(list(range(1, 101)), 95), 95)
        self.assertEqual(percentile([9, 1, 5], 50), 5)
        self.assertIsNone(percentile([], 99))
        result = summarize([{"ok": True, "latency_ms": 1}, {"ok": True, "latency_ms": 3},
                            {"ok": False, "latency_ms": 1000}], 2)
        self.assertEqual(result["errors"], 1)
        self.assertEqual(result["latency_ms"]["p95"], 3)
        self.assertEqual(result["successful_requests_per_second"], 1)

    def test_disposable_stack_cannot_target_the_development_project(self):
        with self.assertRaises(ValueError):
            Stack("minsearch", Path("unused"))
        with self.assertRaises(ValueError):
            Stack("../minsearch-bench-x", Path("unused"))
        self.assertIn("minsearch-bench-test", Stack("minsearch-bench-test", Path("unused")).command)


if __name__ == "__main__":
    unittest.main()
