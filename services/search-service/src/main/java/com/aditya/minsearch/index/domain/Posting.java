package com.aditya.minsearch.index.domain;

import java.util.List;
import java.util.UUID;

public record Posting(UUID documentId, long documentVersion, List<Integer> positions) {
  public Posting {
    // JDK single/pair lists avoid an extra array; larger lists benefit from primitive storage.
    positions = positions.size() <= 2 ? List.copyOf(positions) : new Positions(positions);
    if (positions.isEmpty()) throw new IllegalArgumentException("A posting needs a position");
    for (int i = 1; i < positions.size(); i++) {
      if (positions.get(i) <= positions.get(i - 1))
        throw new IllegalArgumentException("Positions must be sorted");
    }
  }

  public int termFrequency() {
    return positions.size();
  }

  public int positionAt(int index) {
    return positions instanceof Positions packed ? packed.values[index] : positions.get(index);
  }

  public boolean hasPosition(int position) {
    if (positions instanceof Positions packed)
      return java.util.Arrays.binarySearch(packed.values, position) >= 0;
    for (int i = 0; i < positions.size(); i++) {
      if (positions.get(i) == position) return true;
    }
    return false;
  }

  /** Immutable primitive storage; the List API keeps snapshots and callers compatible. */
  private static final class Positions extends java.util.AbstractList<Integer>
      implements java.util.RandomAccess {
    private final int[] values;

    Positions(List<Integer> source) {
      values = new int[source.size()];
      for (int i = 0; i < values.length; i++) values[i] = source.get(i);
    }

    @Override
    public Integer get(int index) {
      return values[index];
    }

    @Override
    public int size() {
      return values.length;
    }

    @Override
    public boolean contains(Object value) {
      return value instanceof Integer position
          && java.util.Arrays.binarySearch(values, position) >= 0;
    }
  }
}
