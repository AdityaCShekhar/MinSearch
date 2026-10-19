package com.aditya.minsearch.cache.infrastructure;

import com.aditya.minsearch.cache.domain.CacheStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class CacheConfiguration {
  @Bean @ConditionalOnProperty(prefix="minsearch.cache.redis", name="enabled", havingValue="true")
  CacheStore redisCacheStore(StringRedisTemplate redis, ObjectMapper mapper) { return new RedisCacheStore(redis,mapper); }
  @Bean @ConditionalOnMissingBean(CacheStore.class)
  CacheStore localCacheStore() { return new CaffeineCacheStore(); }
}
