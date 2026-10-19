package com.aditya.minsearch.shared.domain.event;

import java.util.Objects;
import java.util.UUID;

public record IndexOutcomePayload(
    int schemaVersion,
    UUID documentId,
    long version,
    long generation,
    Outcome outcome,
    String error) {
  public enum Outcome {
    INDEXED,
    DELETED,
    INDEX_FAILED
  }

  public IndexOutcomePayload {
    Objects.requireNonNull(documentId);
    Objects.requireNonNull(outcome);
    if (schemaVersion != 1 || version < 1 || generation < 0) {
      throw new IllegalArgumentException("Unsupported index outcome version");
    }
    if (outcome == Outcome.INDEX_FAILED && (error == null || error.isBlank())) {
      throw new IllegalArgumentException("Failed indexing requires an error description");
    }
    if (outcome != Outcome.INDEX_FAILED && (generation < 1 || error != null)) {
      throw new IllegalArgumentException("Completed indexing requires a published generation");
    }
    if (error != null && error.length() > 512)
      throw new IllegalArgumentException("Index error is too long");
  }
}
