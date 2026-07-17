package com.aditya.minsearch.cache.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CaffeineCacheStoreTest {

  @Test
  void cachesLoadedValues() {
    CaffeineCacheStore cacheStore = new CaffeineCacheStore();
    AtomicInteger loads = new AtomicInteger();

    String first =
        cacheStore.getOrCompute(
            "search:1",
            Duration.ofMinutes(10),
            () -> {
              loads.incrementAndGet();
              return "result";
            });
    String second =
        cacheStore.getOrCompute(
            "search:1",
            Duration.ofMinutes(10),
            () -> {
              loads.incrementAndGet();
              return "other";
            });

    assertThat(first).isEqualTo("result");
    assertThat(second).isEqualTo("result");
    assertThat(loads.get()).isEqualTo(1);
    assertThat(cacheStore.get("search:1", String.class)).contains("result");
  }

  @Test
  void evictsValues() {
    CaffeineCacheStore cacheStore = new CaffeineCacheStore();
    cacheStore.getOrCompute("search:2", Duration.ofMinutes(10), () -> "value");

    cacheStore.evict("search:2");

    assertThat(cacheStore.get("search:2", String.class)).isEmpty();
  }
}
