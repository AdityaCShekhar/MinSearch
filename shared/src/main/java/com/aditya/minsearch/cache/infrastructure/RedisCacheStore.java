package com.aditya.minsearch.cache.infrastructure;

import com.aditya.minsearch.cache.domain.CacheStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis-backed cache using JSON values. Callers retain fail-open behavior through CacheStore users. */
public final class RedisCacheStore implements CacheStore {
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  public RedisCacheStore(StringRedisTemplate redis, ObjectMapper mapper) { this.redis=redis; this.mapper=mapper; }
  @Override public <T> T getOrCompute(String key, Duration ttl, Supplier<T> loader) {
    T value=loader.get();
    try {
      String json=mapper.writeValueAsString(new CachedValue(value.getClass().getName(), mapper.writeValueAsString(value)));
      if(ttl==null || ttl.isZero() || ttl.isNegative()) redis.opsForValue().set(key,json);
      else redis.opsForValue().set(key,json,ttl);
    } catch (Exception cacheFailure) { /* A failed cache write does not fail the request. */ }
    return value;
  }
  @Override public <T> Optional<T> get(String key, Class<T> type) {
    try {
      String json=redis.opsForValue().get(key);
      if(json==null) return Optional.empty();
      CachedValue stored=mapper.readValue(json,CachedValue.class);
      if(!stored.type().equals(type.getName())) return Optional.empty();
      return Optional.of(mapper.readValue(stored.value(),type));
    } catch (Exception cacheFailure) { return Optional.empty(); }
  }
  @Override public void evict(String key) { try { redis.delete(key); } catch (RuntimeException ignored) { /* Cache is best effort. */ } }
  private record CachedValue(String type, String value) {}
}
