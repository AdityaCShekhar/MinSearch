package com.aditya.minsearch.index.application;

import java.util.List;
import java.util.UUID;

public record IndexEvent(UUID eventId, UUID documentId, long documentVersion, Type type,
    List<TextToken> tokens) {
  public enum Type { UPSERT, DELETE }
  public IndexEvent { tokens = tokens == null ? List.of() : List.copyOf(tokens); }
}
