package com.aditya.minsearch.index.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aditya.minsearch.index.domain.IndexGeneration;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IndexPublicationTest {
  @Test
  void publishesOnlyNewerGenerationsAndExposesReadersWithoutLocks() {
    IndexPublication publication = new IndexPublication();
    assertThat(publication.publish(new IndexGenerationBuilder(IndexGeneration.empty()).build(1))).isTrue();
    assertThat(publication.publish(IndexGeneration.empty())).isFalse();
    assertThat(publication.current().generation()).isOne();
  }

  @Test
  void computesCappedExponentialRetryDelays() {
    IndexRetryPolicy policy = new IndexRetryPolicy(5, Duration.ofMillis(100));
    assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofMillis(100));
    assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofMillis(400));
  }
}
