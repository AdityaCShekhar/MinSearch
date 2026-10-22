package com.aditya.minsearch.search.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aditya.minsearch.search.application.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class SearchMetricsConfigurationTest {
  @Test
  void exposesPublishedDocumentCountAndActualCacheLookups() {
    var index = mock(DocumentIndexingService.class);
    when(index.current()).thenReturn(SearchIndexSnapshot.empty());
    var search = new SearchService();
    var registry = new SimpleMeterRegistry();
    new SearchMetricsConfiguration().searchMetrics(search, index).bindTo(registry);
    var snapshot = SearchIndexSnapshot.empty();
    search.search("missing", snapshot.generation(), snapshot.documents(), null, 0, 20);
    search.search("missing", snapshot.generation(), snapshot.documents(), null, 0, 20);
    assertThat(registry.get("minsearch.search.cache.hits").functionCounter().count()).isEqualTo(1);
    assertThat(registry.get("minsearch.search.cache.misses").functionCounter().count())
        .isEqualTo(1);
    assertThat(registry.get("minsearch.index.documents").gauge().value()).isZero();
  }
}
