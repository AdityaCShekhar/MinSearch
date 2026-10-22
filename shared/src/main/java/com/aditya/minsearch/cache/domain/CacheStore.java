package com.aditya.minsearch.cache.domain;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

public interface CacheStore {

  <T> T getOrCompute(String cacheKey, Duration ttl, Supplier<T> loader);

  /** Typed reads preserve DTOs across process boundaries without polymorphic deserialization. */
  default <T> T getOrCompute(String cacheKey, Class<T> type, Duration ttl, Supplier<T> loader) {
    return get(cacheKey, type).orElseGet(() -> getOrCompute(cacheKey, ttl, loader));
  }

  <T> Optional<T> get(String cacheKey, Class<T> valueType);

  void evict(String cacheKey);
}
