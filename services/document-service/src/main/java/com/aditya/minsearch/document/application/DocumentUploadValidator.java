package com.aditya.minsearch.document.application;

import com.aditya.minsearch.document.domain.FileType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class DocumentUploadValidator {
  private DocumentUploadValidator() {}

  public static FileType validate(String filename, String mediaType, long size, long maxSize) {
    if (filename == null || filename.isBlank()) throw new IllegalArgumentException("Filename is required");
    if (size < 1 || size > maxSize) throw new IllegalArgumentException("File size is outside the allowed limit");
    FileType type = FileType.fromFilename(filename);
    String normalizedType = mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT);
    if (!normalizedType.isBlank() && !normalizedType.equals(type.mediaType())) {
      throw new IllegalArgumentException("Filename extension and media type do not match");
    }
    return type;
  }

  public static String safeFilename(String filename) {
    String value = Path.of(filename).getFileName().toString();
    if (value.isBlank() || value.equals(".") || value.equals("..")) {
      throw new IllegalArgumentException("Filename is invalid");
    }
    return value;
  }

  public static String detectMediaType(InputStream content, String filename) throws IOException {
    if (!content.markSupported()) return Files.probeContentType(Path.of(safeFilename(filename)));
    content.mark(8192);
    String detected = Files.probeContentType(Path.of(safeFilename(filename)));
    content.reset();
    return detected;
  }
}
