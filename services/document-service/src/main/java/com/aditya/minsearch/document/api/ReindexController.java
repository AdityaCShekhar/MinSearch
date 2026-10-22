package com.aditya.minsearch.document.api;

import com.aditya.minsearch.document.application.DocumentLifecycleService;
import com.aditya.minsearch.document.domain.Document;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReindexController {
  private final DocumentLifecycleService documents;
  public ReindexController(DocumentLifecycleService documents) { this.documents=documents; }
  @PostMapping("/api/v1/admin/reindex/{id}")
  ResponseEntity<Document> reindex(@PathVariable UUID id, @AuthenticationPrincipal Jwt principal) {
    if (!"ADMIN".equals(principal.getClaimAsString("role"))) throw new org.springframework.security.access.AccessDeniedException("Admin role required");
    return ResponseEntity.accepted().body(documents.reindex(id,UUID.fromString(principal.getSubject()),"ADMIN".equals(principal.getClaimAsString("role"))));
  }
}
