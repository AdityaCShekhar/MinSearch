package com.aditya.minsearch.cache.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers(disabledWithoutDocker = true)
class RedisCacheStoreTest {
  @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
  LettuceConnectionFactory connection;
  StringRedisTemplate template;
  RedisCacheStore cache;

  record Hit(UUID id, double score) {}
  record Page(List<Hit> results, Instant at, long generation) {}

  @BeforeEach void setup() {
    connection = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
    connection.afterPropertiesSet();
    template = new StringRedisTemplate(connection);
    template.afterPropertiesSet();
    cache = new RedisCacheStore(template, JsonMapper.builder().build());
  }
  @AfterEach void close() { connection.destroy(); }

  @Test void versionedDtosAndImmutableListsSurviveAnotherAdapterInstance() {
    String key = UUID.randomUUID().toString();
    Page expected = new Page(List.of(new Hit(UUID.randomUUID(), 1.25)), Instant.parse("2026-10-20T00:00:00Z"), 9);
    cache.getOrCompute(key, Page.class, Duration.ofMinutes(1), () -> expected);
    var restarted = new RedisCacheStore(template, JsonMapper.builder().build());
    assertThat(restarted.get(key, Page.class)).contains(expected);
    assertThat(restarted.get(key, String.class)).isEmpty();
    String listKey = UUID.randomUUID().toString();
    AtomicInteger loads = new AtomicInteger();
    for (int i = 0; i < 2; i++) assertThat(restarted.getOrCompute(listKey, List.class, Duration.ofMinutes(1), () -> {
      loads.incrementAndGet(); return List.of("alpha", "beta");
    })).containsExactly("alpha", "beta");
    assertThat(loads.get()).isOne();
    assertThat(RedisCacheStore.redisKey("sensitive query")).doesNotContain("sensitive");
  }

  @Test void expiryEvictionAndUnknownOrMalformedVersionsAreMisses() throws Exception {
    String key = UUID.randomUUID().toString();
    cache.getOrCompute(key, String.class, Duration.ofMillis(100), () -> "value");
    assertThat(cache.get(key, String.class)).contains("value");
    long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
    while (cache.get(key, String.class).isPresent() && System.nanoTime() < deadline) Thread.sleep(25);
    assertThat(cache.get(key, String.class)).isEmpty();
    for (String invalid : List.of("not-json", "{\"schemaVersion\":2,\"type\":\"java.lang.String\",\"value\":\"\\\"value\\\"\"}")) {
      template.opsForValue().set(RedisCacheStore.redisKey(key), invalid);
      assertThat(cache.get(key, String.class)).isEmpty();
    }
    cache.getOrCompute(key, Duration.ofMinutes(1), () -> "updated");
    cache.evict(key);
    assertThat(cache.get(key, String.class)).isEmpty();
  }

  @Test void failedRedisReadsWritesAndEvictionsDoNotFailTheLoader() {
    var broken = mock(StringRedisTemplate.class);
    when(broken.opsForValue()).thenThrow(new IllegalStateException("cache unavailable"));
    doThrow(new IllegalStateException("cache unavailable")).when(broken).delete(RedisCacheStore.redisKey("key"));
    var unavailable = new RedisCacheStore(broken, JsonMapper.builder().build());
    assertThat(unavailable.getOrCompute("key", String.class, Duration.ofSeconds(1), () -> "computed")).isEqualTo("computed");
    assertThatCode(() -> unavailable.evict("key")).doesNotThrowAnyException();
    assertThatThrownBy(() -> unavailable.getOrCompute("key", String.class, Duration.ofSeconds(1), () -> { throw new IllegalArgumentException("loader failure"); }))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("loader failure");
  }

  @Test void deploymentFlagSelectsOneAdapterWithTheServicesJacksonMapper() {
    new org.springframework.boot.test.context.runner.ApplicationContextRunner()
        .withUserConfiguration(CacheConfiguration.class)
        .withBean(StringRedisTemplate.class, () -> template)
        .withBean(tools.jackson.databind.ObjectMapper.class, () -> JsonMapper.builder().build())
        .withPropertyValues("minsearch.cache.redis.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(com.aditya.minsearch.cache.domain.CacheStore.class)
            .getBean(com.aditya.minsearch.cache.domain.CacheStore.class).isInstanceOf(RedisCacheStore.class));
  }
}
