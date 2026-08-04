package com.aditya.minsearch.index.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndexWorkerTest {
  @Test
  void ignoresDuplicateAndOlderEvents() {
    IndexWorker worker = new IndexWorker();
    UUID document = UUID.randomUUID();
    IndexEvent first = new IndexEvent(UUID.randomUUID(), document, 2, IndexEvent.Type.UPSERT,
        List.of(new TextToken("new", 0, 0, 3)));

    assertThat(worker.process(first).status()).isEqualTo(IndexWorkResult.Status.INDEXED);
    assertThat(worker.process(first).status()).isEqualTo(IndexWorkResult.Status.DUPLICATE);
    IndexEvent old = new IndexEvent(UUID.randomUUID(), document, 1, IndexEvent.Type.UPSERT,
        List.of(new TextToken("old", 0, 0, 3)));
    assertThat(worker.process(old).status()).isEqualTo(IndexWorkResult.Status.OUT_OF_ORDER);
    assertThat(worker.currentGeneration().postingsFor("new").postings()).hasSize(1);
    assertThat(worker.currentGeneration().postingsFor("old").postings()).isEmpty();
  }

  @Test
  void appliesDeletionAndPublishesNewGeneration() {
    IndexWorker worker = new IndexWorker();
    UUID document = UUID.randomUUID();
    worker.process(new IndexEvent(UUID.randomUUID(), document, 1, IndexEvent.Type.UPSERT,
        List.of(new TextToken("term", 0, 0, 4))));
    long before = worker.currentGeneration().generation();

    assertThat(worker.process(new IndexEvent(UUID.randomUUID(), document, 2, IndexEvent.Type.DELETE, List.of())).status())
        .isEqualTo(IndexWorkResult.Status.DELETED);
    assertThat(worker.currentGeneration().generation()).isEqualTo(before + 1);
    assertThat(worker.currentGeneration().document(document).deleted()).isTrue();
  }
}
