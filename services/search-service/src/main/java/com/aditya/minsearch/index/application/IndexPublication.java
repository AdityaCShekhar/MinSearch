package com.aditya.minsearch.index.application;

import com.aditya.minsearch.index.domain.IndexGeneration;
import java.util.concurrent.atomic.AtomicReference;

public final class IndexPublication {
  private final AtomicReference<IndexGeneration> current = new AtomicReference<>(IndexGeneration.empty());

  public IndexGeneration current() { return current.get(); }

  public boolean publish(IndexGeneration next) {
    return current.updateAndGet(previous -> next.generation() > previous.generation() ? next : previous)
        == next;
  }
}
