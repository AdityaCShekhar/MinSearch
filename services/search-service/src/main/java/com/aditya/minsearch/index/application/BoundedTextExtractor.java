package com.aditya.minsearch.index.application;

import com.aditya.minsearch.index.domain.SupportedFileType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public final class BoundedTextExtractor implements TextExtractor {
  private final int maximumCharacters;
  private final int maximumPdfPages;
  private final int maximumPdfBytes;

  public BoundedTextExtractor(int maximumCharacters, int maximumPdfPages) {
    this(maximumCharacters, maximumPdfPages, 10 * 1024 * 1024);
  }

  public BoundedTextExtractor(int maximumCharacters, int maximumPdfPages, int maximumPdfBytes) {
    if (maximumCharacters <= 0 || maximumPdfPages <= 0 || maximumPdfBytes <= 0) {
      throw new IllegalArgumentException("Extraction limits must be positive");
    }
    this.maximumCharacters = maximumCharacters;
    this.maximumPdfPages = maximumPdfPages;
    this.maximumPdfBytes = maximumPdfBytes;
  }

  @Override
  public String extract(InputStream content, SupportedFileType fileType) throws IOException {
    return switch (fileType) {
      case TXT, MARKDOWN -> extractPlainText(content);
      case PDF -> extractPdf(content);
    };
  }

  private String extractPlainText(InputStream content) throws IOException {
    StringBuilder text = new StringBuilder(Math.min(maximumCharacters, 8_192));
    char[] buffer = new char[8_192];
    try (Reader reader = new InputStreamReader(content, StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT))) {
      int read;
      while ((read = reader.read(buffer)) != -1) {
        if (read > maximumCharacters - text.length()) {
          throw limitExceeded();
        }
        for (int i = 0; i < read; i++) if (buffer[i] == 0) throw new TextExtractionException("Binary content is not supported as text");
        text.append(buffer, 0, read);
      }
    }
    return text.toString();
  }

  private String extractPdf(InputStream content) throws IOException {
    return PdfExtractionWorker.extract(readPdfBytes(content), maximumCharacters, maximumPdfPages);
  }

  private byte[] readPdfBytes(InputStream content) throws IOException {
    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    byte[] buffer = new byte[8_192];
    int total = 0;
    int read;
    while ((read = content.read(buffer)) != -1) {
      if (read > maximumPdfBytes - total) {
        throw new TextExtractionException("PDF input limit exceeded");
      }
      output.write(buffer, 0, read);
      total += read;
    }
    return output.toByteArray();
  }

  private TextExtractionException limitExceeded() {
    return new TextExtractionException("Extracted text limit exceeded");
  }
}
