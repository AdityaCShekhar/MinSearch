package com.aditya.minsearch.document.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFilesystemDocumentStorageTest {

  @TempDir Path tempDir;

  @Test
  void storesAndReadsContentUnderConfiguredRoot() throws Exception {
    LocalFilesystemDocumentStorage storage = new LocalFilesystemDocumentStorage(tempDir);

    storage.store(
        "documents/abc.txt", new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

    assertThat(Files.readString(tempDir.resolve("documents/abc.txt"))).isEqualTo("hello");
    try (var input = storage.open("documents/abc.txt")) {
      assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }
  }

  @Test
  void rejectsPathTraversalOutsideRoot() {
    LocalFilesystemDocumentStorage storage = new LocalFilesystemDocumentStorage(tempDir);

    assertThatThrownBy(() -> storage.store("../escape.txt", new ByteArrayInputStream(new byte[0])))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
