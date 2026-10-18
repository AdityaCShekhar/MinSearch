package com.aditya.minsearch.search.application;

import com.aditya.minsearch.index.application.BoundedTextExtractor;
import com.aditya.minsearch.index.application.IndexGenerationBuilder;
import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.index.application.TextTokenizer;
import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.index.domain.SupportedFileType;
import com.aditya.minsearch.search.domain.SearchDocument;
import com.aditya.minsearch.shared.domain.event.DocumentIndexPayload;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentIndexingService {
  private static final Set<String> EVENT_TYPES =
      Set.of("document.uploaded", "document.updated", "document.deleted");
  private final SearchIndexStore store;
  private final Path documentsRoot;
  private final BoundedTextExtractor extractor = new BoundedTextExtractor(10 * 1024 * 1024, 1000);
  private final AtomicReference<SearchIndexSnapshot> published =
      new AtomicReference<>(SearchIndexSnapshot.empty());

  public DocumentIndexingService(
      SearchIndexStore store, @Value("${minsearch.storage.documents-root}") String documentsRoot) {
    this.store = store;
    this.documentsRoot = Path.of(documentsRoot).toAbsolutePath().normalize();
  }

  @PostConstruct
  public synchronized void recover() {
    IndexGenerationBuilder builder = new IndexGenerationBuilder(IndexGeneration.empty());
    var documents = new HashMap<java.util.UUID, SearchDocument>();
    long revision = 0;
    for (SearchIndexStore.StoredMutation stored : store.load()) {
      var mutation = stored.mutation();
      if (mutation.deleted()) builder.delete(mutation.documentId(), mutation.version());
      else {
        builder.replace(mutation.documentId(), mutation.version(), mutation.tokens());
        documents.put(mutation.documentId(), mutation.document());
      }
      revision = Math.max(revision, stored.revision());
    }
    published.set(new SearchIndexSnapshot(builder.build(revision), documents));
  }

  public SearchIndexSnapshot current() {
    return published.get();
  }

  public synchronized void apply(DomainEvent event, DocumentIndexPayload payload)
      throws IOException {
    if (!EVENT_TYPES.contains(event.eventType()))
      throw new IllegalArgumentException("Unsupported document event type");
    SearchIndexSnapshot current = published.get();
    var previous = current.generation().document(payload.documentId());
    // Persisted versions, including deletion tombstones, also deduplicate after a restart.
    if (previous != null && previous.documentVersion() >= payload.version()) return;
    boolean deleted = event.eventType().equals("document.deleted");
    List<TextToken> tokens = deleted ? List.of() : extract(payload);
    SearchDocument metadata =
        new SearchDocument(
            payload.documentId(),
            payload.ownerId(),
            payload.language(),
            payload.fileType().toLowerCase(Locale.ROOT),
            payload.uploadedAt());
    IndexGenerationBuilder builder = new IndexGenerationBuilder(current.generation());
    var documents = new HashMap<>(current.documents());
    if (deleted) {
      builder.delete(payload.documentId(), payload.version());
      documents.remove(payload.documentId());
    } else {
      builder.replace(payload.documentId(), payload.version(), tokens);
      documents.put(payload.documentId(), metadata);
    }
    // Prepare before persistence, then publish only after the projection transaction commits.
    IndexGeneration candidate = builder.build(current.generation().generation() + 1);
    OptionalLong revision =
        store.save(
            new SearchIndexStore.Mutation(
                event.eventId(),
                payload.documentId(),
                payload.version(),
                deleted,
                metadata,
                tokens));
    if (revision.isEmpty()) {
      recover();
      return;
    }
    published.set(
        new SearchIndexSnapshot(
            new IndexGeneration(revision.getAsLong(), candidate.postings(), candidate.documents()),
            documents));
  }

  private List<TextToken> extract(DocumentIndexPayload payload) throws IOException {
    Path file = documentsRoot.resolve(payload.storageKey()).normalize();
    if (!file.startsWith(documentsRoot)
        || !file.toRealPath().startsWith(documentsRoot.toRealPath())) {
      throw new IllegalArgumentException("Document storage key escapes the configured root");
    }
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
    String text;
    try (var content = new DigestInputStream(Files.newInputStream(file), digest)) {
      text = extractor.extract(content, SupportedFileType.valueOf(payload.fileType()));
    }
    if (!HexFormat.of().formatHex(digest.digest()).equals(payload.checksum())) {
      throw new IOException("Document checksum mismatch");
    }
    // Keep surface tokens consistent with QueryEvaluator; stemming needs matching query analysis.
    return new TextTokenizer().tokenize(text);
  }
}
