package com.aditya.minsearch.cache.infrastructure;

import com.aditya.minsearch.cache.domain.CacheStore;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis-backed cache using JSON values. Callers retain fail-open behavior through CacheStore users. */
public final class RedisCacheStore implements CacheStore {
  public static final String NAMESPACE = "minsearch:cache:v1:";
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final com.aditya.minsearch.shared.infrastructure.metrics.CacheMetrics metrics;
  public RedisCacheStore(StringRedisTemplate redis, ObjectMapper mapper) { this(redis, mapper, null); }
  public RedisCacheStore(StringRedisTemplate redis, ObjectMapper mapper, io.micrometer.core.instrument.MeterRegistry registry) {
    this.redis=redis; this.mapper=mapper;
    metrics=new com.aditya.minsearch.shared.infrastructure.metrics.CacheMetrics(registry, "redis");
  }
  @Override public <T> T getOrCompute(String key, Duration ttl, Supplier<T> loader) {
    T value=loader.get();
    try {
      String json=mapper.writeValueAsString(new CachedValue(1, valueType(value.getClass()), mapper.writeValueAsString(value)));
      if(ttl==null || ttl.isZero() || ttl.isNegative()) redis.opsForValue().set(redisKey(key),json);
      else redis.opsForValue().set(redisKey(key),json,ttl);
      metrics.record("write", "success");
    } catch (Exception cacheFailure) { metrics.record("write", "error"); }
    return value;
  }
  @Override public <T> Optional<T> get(String key, Class<T> type) {
    try {
      String json=redis.opsForValue().get(redisKey(key));
      if(json==null) { metrics.record("read", "miss"); return Optional.empty(); }
      CachedValue stored=mapper.readValue(json,CachedValue.class);
      if(stored.schemaVersion()!=1 || !stored.type().equals(valueType(type))) { metrics.record("read", "miss"); return Optional.empty(); }
      T value=mapper.readValue(stored.value(),type);
      metrics.record("read", "hit"); return Optional.of(value);
    } catch (Exception cacheFailure) { metrics.record("read", "error"); return Optional.empty(); }
  }
  @Override public void evict(String key) { try { redis.delete(redisKey(key)); metrics.record("evict", "success"); } catch (RuntimeException ignored) { metrics.record("evict", "error"); } }
  private static String valueType(Class<?> type) {
    if (java.util.List.class.isAssignableFrom(type)) return java.util.List.class.getName();
    if (java.util.Map.class.isAssignableFrom(type)) return java.util.Map.class.getName();
    return type.getName();
  }
  public static String redisKey(String key) {
    try {
      return NAMESPACE + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }
  private record CachedValue(int schemaVersion, String type, String value) {}
}
