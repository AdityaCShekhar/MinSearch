package com.aditya.minsearch.search.application;

import java.util.UUID;

public record SearchResult(UUID documentId, double score, String title, String language, String fileType,
    long version, java.time.Instant uploadedAt, String snippet) {
  public SearchResult(UUID documentId, double score) { this(documentId,score,null,null,null,0,null,null); }
}
