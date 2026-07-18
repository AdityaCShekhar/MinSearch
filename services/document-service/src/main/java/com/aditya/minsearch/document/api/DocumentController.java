package com.aditya.minsearch.document.api;

import com.aditya.minsearch.document.application.DocumentUploadService;
import com.aditya.minsearch.document.application.DocumentQueryService;
import com.aditya.minsearch.document.application.DocumentLifecycleService;
import com.aditya.minsearch.document.domain.Document;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
  private final DocumentUploadService uploads;
  private final DocumentQueryService queries;
  private final DocumentLifecycleService lifecycle;
  public DocumentController(DocumentUploadService uploads, DocumentQueryService queries, DocumentLifecycleService lifecycle) {
    this.uploads = uploads; this.queries = queries; this.lifecycle = lifecycle;
  }

  @PostMapping(consumes = "multipart/form-data")
  public ResponseEntity<Document> upload(
      @RequestHeader("X-User-Id") UUID ownerId,
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "title", required = false) String title,
      @RequestPart(value = "language", required = false) String language) throws Exception {
    return ResponseEntity.accepted().body(uploads.upload(ownerId, file, title, language));
  }

  @GetMapping("/{id}")
  public Document get(@PathVariable UUID id, @RequestHeader("X-User-Id") UUID ownerId) {
    Document document = queries.get(id);
    if (!document.ownerId().equals(ownerId)) throw new IllegalArgumentException("Document not found");
    return document;
  }

  @GetMapping
  public List<Document> list(@RequestHeader("X-User-Id") UUID ownerId) { return queries.list(ownerId); }

  @PutMapping("/{id}")
  public ResponseEntity<Document> update(@PathVariable UUID id, @RequestHeader("X-User-Id") UUID ownerId,
      @RequestPart(value = "title", required = false) String title,
      @RequestPart(value = "language", required = false) String language) {
    return ResponseEntity.accepted().body(lifecycle.update(id, ownerId, title, language));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Document> delete(@PathVariable UUID id, @RequestHeader("X-User-Id") UUID ownerId) {
    return ResponseEntity.accepted().body(lifecycle.delete(id, ownerId));
  }
}
