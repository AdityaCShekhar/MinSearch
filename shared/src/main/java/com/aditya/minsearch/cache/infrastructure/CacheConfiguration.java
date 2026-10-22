package com.aditya.minsearch.cache.infrastructure;

import com.aditya.minsearch.cache.domain.CacheStore;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class CacheConfiguration {
  @Bean @ConditionalOnProperty(prefix="minsearch.cache.redis", name="enabled", havingValue="true")
  CacheStore redisCacheStore(StringRedisTemplate redis, ObjectMapper mapper, org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> registry) { return new RedisCacheStore(redis,mapper,registry.getIfAvailable()); }
  @Bean @ConditionalOnMissingBean(CacheStore.class)
  CacheStore localCacheStore(org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> registry) { return new CaffeineCacheStore(registry.getIfAvailable()); }
}
