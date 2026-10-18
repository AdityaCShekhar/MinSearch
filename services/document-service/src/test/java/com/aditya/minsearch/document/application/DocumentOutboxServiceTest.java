package com.aditya.minsearch.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aditya.minsearch.document.domain.*;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentOutboxJpaEntity;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentOutboxRepository;
import com.aditya.minsearch.shared.domain.event.DocumentIndexPayload;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class DocumentOutboxServiceTest {
  @Test
  void recordsCompleteMetadataAndEscapesStorageKeys() {
    var repository = mock(DocumentOutboxRepository.class);
    var mapper = JsonMapper.builder().build();
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    Document document =
        new Document(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "title",
            "a\"b.txt",
            "documents/a\"b.txt",
            "text/plain",
            FileType.TXT,
            10,
            "abc",
            "en",
            DocumentStatus.UPLOADED,
            1,
            now,
            now,
            null);
    new DocumentOutboxService(repository, mapper).recordUploaded(document, now);
    var captured = ArgumentCaptor.forClass(DocumentOutboxJpaEntity.class);
    verify(repository).save(captured.capture());
    DocumentIndexPayload payload =
        mapper.readValue(captured.getValue().payload(), DocumentIndexPayload.class);
    assertThat(payload.documentId()).isEqualTo(document.id());
    assertThat(payload.ownerId()).isEqualTo(document.ownerId());
    assertThat(payload.storageKey()).isEqualTo(document.storageKey());
    assertThat(payload.uploadedAt()).isEqualTo(now);
    assertThat(payload.fileType()).isEqualTo("TXT");
  }
}
