package com.aditya.minsearch.benchmark;

import com.aditya.minsearch.cache.domain.CacheStore;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

public final class NoCacheStore implements CacheStore {
  public <T> T getOrCompute(String key, Duration ttl, Supplier<T> loader) {
    return loader.get();
  }

  public <T> Optional<T> get(String key, Class<T> type) {
    return Optional.empty();
  }

  public void evict(String key) {}
}
