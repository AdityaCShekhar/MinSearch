package com.aditya.minsearch.index.domain;

import java.util.List;
import java.util.UUID;

public record Posting(UUID documentId, long documentVersion, List<Integer> positions) {
  public Posting {
    positions = List.copyOf(positions);
    if (positions.isEmpty()) throw new IllegalArgumentException("A posting needs a position");
    for (int i = 1; i < positions.size(); i++) {
      if (positions.get(i) <= positions.get(i - 1)) throw new IllegalArgumentException("Positions must be sorted");
    }
  }

  public int termFrequency() { return positions.size(); }
}
