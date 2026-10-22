package com.aditya.minsearch.document.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aditya.minsearch.document.domain.*;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentPersistenceAdapter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentQueryServiceTest {
  @Test
  void onlyTheOwnerCanReadProcessingStatusIncludingDeletion() {
    var adapter = mock(DocumentPersistenceAdapter.class);
    UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
    Instant now = Instant.now();
    var document =
        new Document(
            id,
            owner,
            "title",
            "sample.txt",
            "sample.txt",
            "text/plain",
            FileType.TXT,
            10,
            "checksum",
            "en",
            DocumentStatus.DELETED,
            3,
            now,
            now,
            now);
    when(adapter.findById(id)).thenReturn(Optional.of(document));
    var queries = new DocumentQueryService(adapter);
    assertThat(queries.status(id, owner).status()).isEqualTo(DocumentStatus.DELETED);
    assertThatThrownBy(() -> queries.status(id, UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> queries.get(id,owner,false)).isInstanceOf(IllegalArgumentException.class);
  }
}
