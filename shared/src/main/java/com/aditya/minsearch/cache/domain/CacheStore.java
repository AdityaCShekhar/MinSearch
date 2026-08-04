package com.aditya.minsearch.cache.domain;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

public interface CacheStore {

  <T> T getOrCompute(String cacheKey, Duration ttl, Supplier<T> loader);

  <T> Optional<T> get(String cacheKey, Class<T> valueType);

  void evict(String cacheKey);
}
