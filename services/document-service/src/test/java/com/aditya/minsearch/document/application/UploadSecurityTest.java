package com.aditya.minsearch.document.application;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UploadSecurityTest {
  @Test void inspectsContentAndSanitizesClientPaths() throws Exception {
    assertThat(DocumentUploadValidator.safeFilename("C:\\fakepath\\notes.txt")).isEqualTo("notes.txt");
    assertThat(DocumentUploadValidator.safeFilename("../../notes.txt")).isEqualTo("notes.txt");
    for (String name : new String[]{"bad\n.txt", "x".repeat(1025), ".."})
      assertThatThrownBy(() -> DocumentUploadValidator.safeFilename(name)).isInstanceOf(IllegalArgumentException.class);
    assertThat(DocumentUploadValidator.detectMediaType(stream("%PDF-1.7\n"), "ok.pdf")).isEqualTo("application/pdf");
    assertThatThrownBy(() -> DocumentUploadValidator.detectMediaType(stream("hello"), "fake.pdf")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DocumentUploadValidator.detectMediaType(stream("%PDF-1.7"), "fake.txt")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DocumentUploadValidator.detectMediaType(stream("a\0b"), "binary.txt")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DocumentUploadValidator.metadata("a".repeat(501), "en")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DocumentUploadValidator.metadata("ok", "../../etc")).isInstanceOf(IllegalArgumentException.class);
  }
  private ByteArrayInputStream stream(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
}
