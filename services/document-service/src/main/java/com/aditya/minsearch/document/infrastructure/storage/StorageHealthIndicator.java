package com.aditya.minsearch.document.infrastructure.storage;

import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.*;
import org.springframework.stereotype.Component;

@Component("storageHealthIndicator")
public final class StorageHealthIndicator implements HealthIndicator {
  private final Path root;
  public StorageHealthIndicator(@Value("${minsearch.storage.documents-root}") String root) { this.root=Path.of(root); }
  @Override public Health health() {
    try {
      Files.createDirectories(root);
      Path probe=Files.createTempFile(root, ".health-", ".tmp");
      try { Files.writeString(probe, "ready"); } finally { Files.deleteIfExists(probe); }
      return Health.up().build();
    } catch (Exception failure) { return Health.down().build(); }
  }
}
