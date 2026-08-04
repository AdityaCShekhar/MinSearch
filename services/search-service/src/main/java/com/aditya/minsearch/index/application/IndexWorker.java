package com.aditya.minsearch.index.application;

import com.aditya.minsearch.index.domain.IndexGeneration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class IndexWorker {
  private final Map<UUID, Long> appliedVersions = new HashMap<>();
  private final Set<UUID> appliedEvents = new HashSet<>();
  private IndexGeneration generation = IndexGeneration.empty();
  private int attempts;

  public synchronized IndexWorkResult process(IndexEvent event) {
    if (appliedEvents.contains(event.eventId())) {
      return new IndexWorkResult(IndexWorkResult.Status.DUPLICATE, attempts, null);
    }
    long current = appliedVersions.getOrDefault(event.documentId(), -1L);
    if (event.documentVersion() < current) {
      appliedEvents.add(event.eventId());
      return new IndexWorkResult(IndexWorkResult.Status.OUT_OF_ORDER, attempts, null);
    }
    attempts++;
    try {
      IndexGenerationBuilder builder = new IndexGenerationBuilder(generation);
      if (event.type() == IndexEvent.Type.DELETE) builder.delete(event.documentId(), event.documentVersion());
      else builder.replace(event.documentId(), event.documentVersion(), event.tokens());
      generation = builder.build(generation.generation() + 1);
      appliedVersions.put(event.documentId(), event.documentVersion());
      appliedEvents.add(event.eventId());
      return new IndexWorkResult(event.type() == IndexEvent.Type.DELETE
          ? IndexWorkResult.Status.DELETED : IndexWorkResult.Status.INDEXED, attempts, null);
    } catch (RuntimeException failure) {
      return new IndexWorkResult(IndexWorkResult.Status.RETRYABLE_FAILURE, attempts, failure.getMessage());
    }
  }

  public synchronized IndexGeneration currentGeneration() { return generation; }
}
