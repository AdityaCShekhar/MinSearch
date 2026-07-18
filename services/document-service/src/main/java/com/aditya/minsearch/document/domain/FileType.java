package com.aditya.minsearch.document.domain;

public enum FileType {
  TXT("text/plain"),
  MARKDOWN("text/markdown"),
  PDF("application/pdf");

  private final String mediaType;

  FileType(String mediaType) {
    this.mediaType = mediaType;
  }

  public String mediaType() {
    return mediaType;
  }

  public static FileType fromFilename(String filename) {
    String lower = filename.toLowerCase(java.util.Locale.ROOT);
    if (lower.endsWith(".txt")) return TXT;
    if (lower.endsWith(".md")) return MARKDOWN;
    if (lower.endsWith(".pdf")) return PDF;
    throw new IllegalArgumentException("Only .txt, .md, and .pdf files are supported");
  }
}
