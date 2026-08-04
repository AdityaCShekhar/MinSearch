package com.aditya.minsearch.document.application;

import com.aditya.minsearch.document.domain.*;
import com.aditya.minsearch.document.domain.storage.DocumentStorage;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentPersistenceAdapter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {
  private final DocumentPersistenceAdapter documents;
  private final DocumentStorage storage;
  private final Clock clock;
  private final long maxSize;
  private final DocumentOutboxService outbox;

  public DocumentUploadService(DocumentPersistenceAdapter documents, DocumentStorage storage, Clock clock,
      DocumentOutboxService outbox, @Value("${minsearch.documents.max-size-bytes}") long maxSize) {
    this.documents=documents; this.storage=storage; this.clock=clock; this.outbox=outbox; this.maxSize=maxSize;
  }

  @Transactional
  public Document upload(UUID ownerId, MultipartFile file, String title, String language) throws Exception {
    String filename = DocumentUploadValidator.safeFilename(file.getOriginalFilename());
    FileType type = DocumentUploadValidator.validate(filename, file.getContentType(), file.getSize(), maxSize);
    UUID id = UUID.randomUUID();
    String key = "documents/" + id + "/1-" + filename;
    try (InputStream content = file.getInputStream()) { storage.store(key, content); }
    try {
      Instant now = clock.instant();
      Document document = new Document(id, ownerId, title == null || title.isBlank() ? filename : title.trim(),
          filename, key, type.mediaType(), type, file.getSize(), checksum(file), language,
          DocumentStatus.UPLOADED, 1, now, now, null);
      Document saved = documents.save(document);
      outbox.recordUploaded(saved, now);
      return saved;
    } catch (Exception exception) {
      storage.delete(key);
      throw exception;
    }
  }

  private static String checksum(MultipartFile file) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
  }
}
