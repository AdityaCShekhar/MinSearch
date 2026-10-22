package com.aditya.minsearch.document.api;

import com.aditya.minsearch.document.application.DocumentLifecycleService;
import com.aditya.minsearch.document.application.DocumentQueryService;
import com.aditya.minsearch.document.application.DocumentUploadService;
import com.aditya.minsearch.document.domain.Document;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
  private final DocumentUploadService uploads;
  private final DocumentQueryService queries;
  private final DocumentLifecycleService lifecycle;
  private final com.aditya.minsearch.document.application.DocumentContentService content;

  public DocumentController(
      DocumentUploadService uploads,
      DocumentQueryService queries,
      DocumentLifecycleService lifecycle, com.aditya.minsearch.document.application.DocumentContentService content) {
    this.uploads = uploads;
    this.queries = queries;
    this.lifecycle = lifecycle;
    this.content = content;
  }

  @PostMapping(consumes = "multipart/form-data")
  public ResponseEntity<Document> upload(
      @AuthenticationPrincipal Jwt principal,
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "title", required = false) String title,
      @RequestPart(value = "language", required = false) String language)
      throws Exception {
    return ResponseEntity.accepted()
        .body(uploads.upload(UUID.fromString(principal.getSubject()), file, title, language));
  }

  @GetMapping("/{id}")
  public Document get(@PathVariable UUID id, @AuthenticationPrincipal Jwt principal) {
    UUID ownerId = UUID.fromString(principal.getSubject());
    return queries.get(id,ownerId,admin(principal));
  }

  @GetMapping
  public List<Document> list(@AuthenticationPrincipal Jwt principal, @RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="20") int size) {
    return queries.list(UUID.fromString(principal.getSubject()),admin(principal),page,size);
  }

  @GetMapping("/{id}/status")
  public DocumentQueryService.ProcessingStatus status(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt principal) {
    return queries.status(id, UUID.fromString(principal.getSubject()),admin(principal));
  }

  @PutMapping("/{id}")
  public ResponseEntity<Document> update(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt principal,
      @RequestPart(value = "title", required = false) String title,
      @RequestPart(value = "language", required = false) String language,
      @RequestPart(value = "file", required = false) MultipartFile file) throws Exception {
    return ResponseEntity.accepted()
        .body(file==null ? lifecycle.update(id, UUID.fromString(principal.getSubject()), title, language,admin(principal))
            : content.replace(id,UUID.fromString(principal.getSubject()),admin(principal),file,title,language));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Document> delete(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt principal) {
    return ResponseEntity.accepted()
        .body(lifecycle.delete(id, UUID.fromString(principal.getSubject()),admin(principal)));
  }
  private static boolean admin(Jwt principal) { return "ADMIN".equals(principal.getClaimAsString("role")); }
}
