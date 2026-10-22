package com.aditya.minsearch.document.infrastructure.storage;

import com.aditya.minsearch.document.domain.storage.DocumentStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LocalFilesystemDocumentStorage implements DocumentStorage {

  private final Path rootDirectory;

  public LocalFilesystemDocumentStorage(Path rootDirectory) {
    this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
  }

  @Override
  public String store(String storageKey, InputStream content) throws IOException {
    Path resolvedPath = resolve(storageKey);
    Files.createDirectories(resolvedPath.getParent());
    Path temporary=Files.createTempFile(resolvedPath.getParent(), ".upload-", ".tmp");
    try {
      try (var channel=java.nio.channels.FileChannel.open(temporary,java.nio.file.StandardOpenOption.WRITE)) {
        content.transferTo(java.nio.channels.Channels.newOutputStream(channel));
        channel.force(true);
      }
      Files.move(temporary,resolvedPath,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    } finally { Files.deleteIfExists(temporary); }
    return storageKey;
  }

  @Override
  public InputStream open(String storageKey) throws IOException {
    return Files.newInputStream(resolve(storageKey));
  }

  @Override
  public void delete(String storageKey) throws IOException {
    Files.deleteIfExists(resolve(storageKey));
  }

  Path resolve(String storageKey) {
    Path resolvedPath = rootDirectory.resolve(storageKey).normalize();
    if (!resolvedPath.startsWith(rootDirectory)) {
      throw new IllegalArgumentException("Storage key escapes the configured root directory");
    }
    return resolvedPath;
  }
}
