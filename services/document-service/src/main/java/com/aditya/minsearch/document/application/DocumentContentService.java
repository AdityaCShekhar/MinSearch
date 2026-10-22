package com.aditya.minsearch.document.application;

import com.aditya.minsearch.document.domain.*;
import com.aditya.minsearch.document.domain.storage.DocumentStorage;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentPersistenceAdapter;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentContentService {
  private final DocumentPersistenceAdapter documents;
  private final DocumentStorage storage;
  private final DocumentOutboxService outbox;
  private final Clock clock;
  private final long maxSize;
  public DocumentContentService(DocumentPersistenceAdapter documents, DocumentStorage storage, DocumentOutboxService outbox,
      Clock clock, @Value("${minsearch.documents.max-size-bytes}") long maxSize) {
    this.documents=documents; this.storage=storage; this.outbox=outbox; this.clock=clock; this.maxSize=maxSize;
  }
  @Transactional
  public Document replace(UUID id, UUID actor, boolean admin, MultipartFile file, String title, String language) throws Exception {
    var current=documents.findByIdForUpdate(id).filter(doc -> doc.deletedAt()==null && (admin || doc.ownerId().equals(actor)))
        .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    String filename=DocumentUploadValidator.safeFilename(file.getOriginalFilename());
    var type=DocumentUploadValidator.validate(filename,file.getContentType(),file.getSize(),maxSize);
    DocumentUploadValidator.metadata(title,language);
    try (var input=file.getInputStream()) { DocumentUploadValidator.detectMediaType(input,filename); }
    String checksum=DocumentUploadService.checksum(file);
    String nextTitle=title==null || title.isBlank() ? current.title() : title.trim();
    String nextLanguage=language==null ? current.language() : language;
    boolean sameContent=checksum.equals(current.checksum()) && type==current.fileType();
    if (sameContent && Objects.equals(nextTitle,current.title()) && Objects.equals(nextLanguage,current.language())) return current;
    String key=sameContent ? current.storageKey() : "documents/"+id+"/"+UUID.randomUUID()+".bin";
    if (!sameContent) {
      try (var input=file.getInputStream()) { storage.store(key,input); }
      StoredFileCompensation.onRollback(storage,key);
    }
    try {
      var updated=new Document(id,current.ownerId(),nextTitle,filename,key,type.mediaType(),type,file.getSize(),checksum,nextLanguage,
          DocumentStatus.UPLOADED,current.currentVersion()+1,current.createdAt(),clock.instant(),null);
      var saved=documents.save(updated); outbox.recordUpdated(saved,saved.updatedAt()); return saved;
    } catch (Exception failure) { if (!sameContent) storage.delete(key); throw failure; }
  }
}
