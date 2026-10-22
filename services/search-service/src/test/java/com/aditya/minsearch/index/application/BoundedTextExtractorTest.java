package com.aditya.minsearch.index.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aditya.minsearch.index.domain.SupportedFileType;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class BoundedTextExtractorTest {
  @Test void rejectsMalformedUtf8AndEmbeddedBinary() {
    var extractor = new BoundedTextExtractor(100, 10);
    assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(new byte[]{(byte)0xc3, 0x28}), SupportedFileType.TXT))
        .isInstanceOf(java.io.IOException.class);
    assertThatThrownBy(() -> extractor.extract(stream("a\0b"), SupportedFileType.TXT))
        .isInstanceOf(TextExtractionException.class);
  }
  @Test void pdfWorkerEnforcesOutputAndTimeBudgets() throws Exception {
    var extractor = new BoundedTextExtractor(3, 10);
    assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(pdf("more than three")), SupportedFileType.PDF))
        .isInstanceOf(TextExtractionException.class).hasMessage("Extracted text limit exceeded");
    String previous = System.getProperty("minsearch.pdf.timeout-seconds");
    try {
      System.setProperty("minsearch.pdf.timeout-seconds", "0");
      assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(pdf("test")), SupportedFileType.PDF))
          .isInstanceOf(TextExtractionException.class).hasMessage("PDF extraction time limit exceeded");
    } finally {
      if (previous == null) System.clearProperty("minsearch.pdf.timeout-seconds");
      else System.setProperty("minsearch.pdf.timeout-seconds", previous);
    }
  }
  @Test
  void extractsUtf8TextAndMarkdownWithoutChangingContent() throws Exception {
    BoundedTextExtractor extractor = new BoundedTextExtractor(100, 10);

    assertThat(extractor.extract(stream("नमस्ते **world**"), SupportedFileType.TXT))
        .isEqualTo("नमस्ते **world**");
    assertThat(extractor.extract(stream("# Heading\n\nbody"), SupportedFileType.MARKDOWN))
        .isEqualTo("# Heading\n\nbody");
  }

  @Test
  void rejectsTextThatExceedsCharacterLimit() {
    BoundedTextExtractor extractor = new BoundedTextExtractor(4, 10);

    assertThatThrownBy(() -> extractor.extract(stream("12345"), SupportedFileType.TXT))
        .isInstanceOf(TextExtractionException.class).hasMessage("Extracted text limit exceeded");
  }

  @Test
  void extractsPdfText() throws Exception {
    BoundedTextExtractor extractor = new BoundedTextExtractor(100, 10);

    assertThat(extractor.extract(new ByteArrayInputStream(pdf("pdf content")), SupportedFileType.PDF))
        .contains("pdf content");
  }

  @Test
  void rejectsPdfThatExceedsPageLimit() throws Exception {
    BoundedTextExtractor extractor = new BoundedTextExtractor(100, 1);

    assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(pdfPages(2)), SupportedFileType.PDF))
        .isInstanceOf(TextExtractionException.class).hasMessage("PDF page limit exceeded");
  }

  @Test
  void rejectsPdfInputThatExceedsByteLimit() {
    BoundedTextExtractor extractor = new BoundedTextExtractor(100, 10, 4);

    assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(new byte[5]), SupportedFileType.PDF))
        .isInstanceOf(TextExtractionException.class).hasMessage("PDF input limit exceeded");
  }

  private static ByteArrayInputStream stream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] pdf(String content) throws Exception {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(50, 700);
        stream.showText(content);
        stream.endText();
      }
      java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
      document.save(output);
      return output.toByteArray();
    }
  }

  private static byte[] pdfPages(int count) throws Exception {
    try (PDDocument document = new PDDocument()) {
      for (int index = 0; index < count; index++) {
        document.addPage(new PDPage());
      }
      java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
      document.save(output);
      return output.toByteArray();
    }
  }
}
