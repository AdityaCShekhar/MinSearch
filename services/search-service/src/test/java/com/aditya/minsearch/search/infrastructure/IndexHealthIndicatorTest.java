package com.aditya.minsearch.search.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aditya.minsearch.search.application.*;
import org.junit.jupiter.api.Test;

class IndexHealthIndicatorTest {
  @Test void requiresCompleteRecoveryAndFailsClosedOnRecoveryError() {
    var store=mock(SearchIndexStore.class);
    var indexing=new DocumentIndexingService(store, System.getProperty("java.io.tmpdir"));
    var health=new IndexHealthIndicator(indexing);
    assertThat(health.health().getStatus().getCode()).isEqualTo("DOWN");
    indexing.recover();
    assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
    doThrow(new IllegalStateException("unavailable")).when(store).forEach(any());
    assertThatThrownBy(indexing::recover).isInstanceOf(IllegalStateException.class);
    assertThat(health.health().getStatus().getCode()).isEqualTo("DOWN");
    assertThat(indexing.current().generation().documentCount()).isZero();
  }
}
