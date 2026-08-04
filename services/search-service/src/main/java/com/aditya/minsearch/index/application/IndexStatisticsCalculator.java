package com.aditya.minsearch.index.application;

import com.aditya.minsearch.index.domain.CorpusStatistics;
import com.aditya.minsearch.index.domain.IndexGeneration;
import java.util.Map;

public final class IndexStatisticsCalculator {
  public CorpusStatistics corpus(IndexGeneration generation) {
    long total = generation.documents().values().stream().filter(document -> !document.deleted())
        .mapToLong(document -> document.documentLength()).sum();
    int count = generation.documentCount();
    return new CorpusStatistics(count, total, count == 0 ? 0.0 : (double) total / count);
  }

  public Map<String, Integer> documentFrequency(IndexGeneration generation) {
    return generation.postings().entrySet().stream()
        .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
            entry -> entry.getValue().postings().size()));
  }
}
