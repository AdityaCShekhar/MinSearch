package com.aditya.minsearch.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aditya.minsearch.document.domain.FileType;
import org.junit.jupiter.api.Test;

class DocumentUploadValidatorTest {
  @Test
  void acceptsSupportedExtensionAndMatchingMediaType() {
    assertThat(DocumentUploadValidator.validate("notes.md", "text/markdown", 12, 100))
        .isEqualTo(FileType.MARKDOWN);
  }

  @Test
  void rejectsUnsupportedExtensionAndOversizedContent() {
    assertThatThrownBy(() -> DocumentUploadValidator.validate("notes.docx", "application/octet-stream", 12, 100))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DocumentUploadValidator.validate("notes.txt", "text/plain", 101, 100))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void stripsPathComponentsFromOriginalFilename() {
    assertThat(DocumentUploadValidator.safeFilename("../../notes.txt")).isEqualTo("notes.txt");
  }
}
