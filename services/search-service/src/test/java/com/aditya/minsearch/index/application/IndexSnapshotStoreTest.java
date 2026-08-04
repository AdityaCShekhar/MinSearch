package com.aditya.minsearch.index.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aditya.minsearch.index.domain.IndexGeneration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexSnapshotStoreTest {
  @TempDir Path directory;

  @Test
  void savesChecksumsAndRecoversGeneration() throws Exception {
    UUID id = UUID.randomUUID();
    IndexGeneration generation = new IndexGenerationBuilder(IndexGeneration.empty())
        .replace(id, 4, List.of(new TextToken("term", 0, 0, 4))).build(9);
    Path snapshot = directory.resolve("generation-9.bin");
    IndexSnapshotStore store = new IndexSnapshotStore();

    store.save(generation, snapshot);
    IndexGeneration recovered = store.load(snapshot);

    assertThat(recovered.generation()).isEqualTo(9);
    assertThat(recovered.postingsFor("term").forDocument(id).documentVersion()).isEqualTo(4);
    assertThat(Files.exists(directory.resolve("generation-9.bin.sha256"))).isTrue();
  }

  @Test
  void rejectsCorruptedSnapshot() throws Exception {
    Path snapshot = directory.resolve("generation.bin");
    IndexSnapshotStore store = new IndexSnapshotStore();
    store.save(IndexGeneration.empty(), snapshot);
    Files.write(snapshot, new byte[] {1, 2, 3});

    assertThatThrownBy(() -> store.load(snapshot)).isInstanceOf(java.io.IOException.class)
        .hasMessage("Index snapshot checksum mismatch");
  }
}
