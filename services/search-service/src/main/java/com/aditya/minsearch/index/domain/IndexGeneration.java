package com.aditya.minsearch.index.domain;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public final class IndexGeneration {
  private static final PostingList EMPTY_POSTINGS = new PostingList(java.util.List.of());
  private final long generation;
  private final Map<String, PostingList> postings;
  private final Map<UUID, IndexedDocument> documents;
  private final int documentCount;
  private volatile java.util.List<String> vocabulary;

  public IndexGeneration(
      long generation, Map<String, PostingList> postings, Map<UUID, IndexedDocument> documents) {
    this.generation = generation;
    this.postings = Map.copyOf(postings);
    this.documents = Map.copyOf(documents);
    this.documentCount = documents.values().stream().mapToInt(d -> d.deleted() ? 0 : 1).sum();
  }

  public static IndexGeneration empty() {
    return new IndexGeneration(0, Map.of(), Map.of());
  }

  public long generation() {
    return generation;
  }

  public Map<String, PostingList> postings() {
    return Collections.unmodifiableMap(postings);
  }

  public Map<UUID, IndexedDocument> documents() {
    return Collections.unmodifiableMap(documents);
  }

  public PostingList postingsFor(String term) {
    return postings.getOrDefault(term, EMPTY_POSTINGS);
  }

  public IndexedDocument document(UUID id) {
    return documents.get(id);
  }

  public int documentCount() {
    return documentCount;
  }

  public int termCount() {
    return postings.size();
  }

  /** Benign duplicate computation on first use keeps generation reads free of locks. */
  public java.util.List<String> vocabulary() {
    var cached = vocabulary;
    if (cached == null) {
      cached = postings.keySet().stream().sorted().toList();
      vocabulary = cached;
    }
    return cached;
  }
}
