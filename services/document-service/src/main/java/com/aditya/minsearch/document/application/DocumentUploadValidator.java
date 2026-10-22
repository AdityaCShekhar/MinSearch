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
    String normalizedType = mediaType == null ? "" : mediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    if (!normalizedType.isBlank() && !normalizedType.equals(type.mediaType())) {
      throw new IllegalArgumentException("Filename extension and media type do not match");
    }
    return type;
  }

  public static String safeFilename(String filename) {
    if (filename == null || filename.length() > 1024 || filename.chars().anyMatch(c -> c < 32 || c == 127))
      throw new IllegalArgumentException("Filename is invalid");
    String value = Path.of(filename.replace('\\', '/')).getFileName().toString();
    if (value.isBlank() || value.equals(".") || value.equals("..") || value.length() > 255) {
      throw new IllegalArgumentException("Filename is invalid");
    }
    return value;
  }

  public static String detectMediaType(InputStream content, String filename) throws IOException {
    byte[] sample = content.readNBytes(8192);
    if (sample.length == 0) throw new IllegalArgumentException("File content is empty");
    boolean pdf = new String(sample, java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF-");
    FileType type = FileType.fromFilename(filename);
    if (type == FileType.PDF) {
      if (!pdf) throw new IllegalArgumentException("PDF content does not match its declared type");
      return "application/pdf";
    }
    if (pdf || java.util.stream.IntStream.range(0, sample.length).anyMatch(i -> sample[i] == 0))
      throw new IllegalArgumentException("Text content does not match its declared type");
    return type.mediaType();
  }

  public static void metadata(String title, String language) {
    if (title != null && (title.length() > 500 || title.chars().anyMatch(c -> c == 0)))
      throw new IllegalArgumentException("Title exceeds the allowed limit");
    if (language != null && !language.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8}){0,2}"))
      throw new IllegalArgumentException("Language must be a supported BCP-47-style tag");
    if (language != null && language.length() > 16) throw new IllegalArgumentException("Language tag is too long");
  }
}
