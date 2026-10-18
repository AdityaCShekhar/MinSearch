package com.aditya.minsearch.shared.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Versioned document metadata needed by an independently deployed indexer. */
public record DocumentIndexPayload(
    int schemaVersion,
    UUID documentId,
    UUID ownerId,
    long version,
    String storageKey,
    String checksum,
    String language,
    String fileType,
    Instant uploadedAt) {
  public DocumentIndexPayload {
    if (schemaVersion != 1 || version < 1)
      throw new IllegalArgumentException("Unsupported document event version");
    Objects.requireNonNull(documentId);
    Objects.requireNonNull(ownerId);
    Objects.requireNonNull(storageKey);
    Objects.requireNonNull(checksum);
    Objects.requireNonNull(fileType);
    Objects.requireNonNull(uploadedAt);
  }
}
