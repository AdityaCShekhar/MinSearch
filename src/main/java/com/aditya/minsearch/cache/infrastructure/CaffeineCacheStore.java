package com.aditya.minsearch.cache.infrastructure;

import com.aditya.minsearch.cache.domain.CacheStore;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

public class CaffeineCacheStore implements CacheStore {

  private record CachedValue(Object value, long expiresAtNanos) {}

  private final Cache<String, CachedValue> cache;

  public CaffeineCacheStore() {
    this(Caffeine.newBuilder().maximumSize(10_000).build());
  }

  public CaffeineCacheStore(Cache<String, CachedValue> cache) {
    this.cache = cache;
  }

  @Override
  public <T> T getOrCompute(String cacheKey, Duration ttl, Supplier<T> loader) {
    CachedValue cached = cache.getIfPresent(cacheKey);
    if (cached != null && !isExpired(cached)) {
      @SuppressWarnings("unchecked")
      T value = (T) cached.value();
      return value;
    }

    T loaded = loader.get();
    cache.put(cacheKey, new CachedValue(loaded, expirationNanos(ttl)));
    return loaded;
  }

  @Override
  public <T> Optional<T> get(String cacheKey, Class<T> valueType) {
    CachedValue cached = cache.getIfPresent(cacheKey);
    if (cached == null || isExpired(cached) || !valueType.isInstance(cached.value())) {
      return Optional.empty();
    }
    return Optional.of(valueType.cast(cached.value()));
  }

  @Override
  public void evict(String cacheKey) {
    cache.invalidate(cacheKey);
  }

  private static boolean isExpired(CachedValue cachedValue) {
    return cachedValue.expiresAtNanos() != Long.MAX_VALUE
        && System.nanoTime() >= cachedValue.expiresAtNanos();
  }

  private static long expirationNanos(Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      return Long.MAX_VALUE;
    }
    long candidate = System.nanoTime() + ttl.toNanos();
    return candidate < 0 ? Long.MAX_VALUE : candidate;
  }
}
