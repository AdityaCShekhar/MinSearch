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
  private com.aditya.minsearch.shared.infrastructure.WorkerCoordinator workers =
      new com.aditya.minsearch.shared.infrastructure.WorkerCoordinator(java.time.Duration.ofSeconds(25));
  @org.springframework.beans.factory.annotation.Autowired
  void workers(com.aditya.minsearch.shared.infrastructure.WorkerCoordinator workers) { this.workers=workers; }

  private static final Set<String> EVENT_TYPES =
      Set.of("document.uploaded", "document.updated", "document.deleted");
  private final SearchIndexStore store;
  private final Path documentsRoot;
  private final BoundedTextExtractor extractor;
  private final TextTokenizer tokenizer;
  private volatile boolean recovered;
  private io.micrometer.core.instrument.MeterRegistry metrics = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
  @org.springframework.beans.factory.annotation.Autowired
  void metrics(io.micrometer.core.instrument.MeterRegistry metrics) { this.metrics = metrics; }
  public boolean recovered() { return recovered; }
  private final AtomicReference<SearchIndexSnapshot> published =
      new AtomicReference<>(SearchIndexSnapshot.empty());

  public DocumentIndexingService(
      SearchIndexStore store, @Value("${minsearch.storage.documents-root}") String documentsRoot) {
    this(store, documentsRoot, 5_000_000, 1000, 10 * 1024 * 1024, 100_000, 256);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public DocumentIndexingService(SearchIndexStore store,
      @Value("${minsearch.storage.documents-root}") String documentsRoot,
      @Value("${minsearch.extraction.max-characters:5000000}") int characters,
      @Value("${minsearch.extraction.max-pdf-pages:1000}") int pages,
      @Value("${minsearch.extraction.max-input-bytes:10485760}") int bytes,
      @Value("${minsearch.extraction.max-tokens:100000}") int tokens,
      @Value("${minsearch.extraction.max-term-length:256}") int termLength) {
    this.store = store;
    this.documentsRoot = Path.of(documentsRoot).toAbsolutePath().normalize();
    this.extractor = new BoundedTextExtractor(characters, pages, bytes);
    this.tokenizer = new TextTokenizer(tokens, termLength);
  }

  @PostConstruct
  public synchronized void recover() {
    recovered = false;
    IndexGenerationBuilder builder = new IndexGenerationBuilder(IndexGeneration.empty());
    var documents = new HashMap<java.util.UUID, SearchDocument>();
    long[] revision = {0};
    store.forEach(
        stored -> {
          var mutation = stored.mutation();
          if (mutation.deleted()) builder.delete(mutation.documentId(), mutation.version());
          else {
            builder.replace(mutation.documentId(), mutation.version(), mutation.tokens());
            documents.put(mutation.documentId(), mutation.document());
          }
          revision[0] = Math.max(revision[0], stored.revision());
        });
    published.set(new SearchIndexSnapshot(builder.build(revision[0]), documents));
    recovered = true;
  }

  public SearchIndexSnapshot current() {
    return published.get();
  }

  public void apply(DomainEvent event, DocumentIndexPayload payload) throws IOException {
    var sample = io.micrometer.core.instrument.Timer.start(metrics);
    String outcome = "success";
    try {
      applyInternal(event, payload);
      metrics.counter("minsearch.index.events", "outcome", "success").increment();
    } catch (IOException | RuntimeException failure) {
      outcome = "error";
      metrics.counter("minsearch.index.events", "outcome", "error").increment();
      throw failure;
    } finally {
      sample.stop(metrics.timer("minsearch.index.duration", "outcome", outcome));
      metrics.summary("minsearch.index.event.age.seconds").record(Math.max(0, java.time.Duration.between(event.occurredAt(), java.time.Instant.now()).toMillis() / 1000.0));
    }
  }

  private void applyInternal(DomainEvent event, DocumentIndexPayload payload) throws IOException {
    try (var lease=workers.acquire()) {
      applyAdmitted(event, payload);
    }
  }

  private void applyAdmitted(DomainEvent event, DocumentIndexPayload payload) throws IOException {
    if (!EVENT_TYPES.contains(event.eventType()))
      throw new IllegalArgumentException("Unsupported document event type");
    var previous = published.get().generation().document(payload.documentId());
    // Persisted versions, including deletion tombstones, also deduplicate after a restart.
    if (previous != null && previous.documentVersion() >= payload.version()) return;
    boolean deleted = event.eventType().equals("document.deleted");
    Extracted extracted=deleted ? new Extracted(List.of(),null) : extract(payload);
    List<TextToken> tokens = extracted.tokens();
    SearchDocument metadata =
        new SearchDocument(
            payload.documentId(),
            payload.ownerId(),
            payload.language(),
            payload.fileType().toLowerCase(Locale.ROOT),
            payload.uploadedAt(),payload.title(),4096);
    // Independent extraction can run concurrently; recheck versions under the publication lock.
    synchronized (this) {
      SearchIndexSnapshot current = published.get();
      previous = current.generation().document(payload.documentId());
      if (previous != null && previous.documentVersion() >= payload.version()) return;
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
                  tokens,extracted.text()));
      if (revision.isEmpty()) {
        recover();
        return;
      }
      published.set(
          new SearchIndexSnapshot(
              new IndexGeneration(
                  revision.getAsLong(), candidate.postings(), candidate.documents()),
              documents));
    }
  }

  private record Extracted(List<TextToken> tokens, String text) {}
  private Extracted extract(DocumentIndexPayload payload) throws IOException {
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
    var tokens=new java.util.ArrayList<TextToken>();
    tokens.addAll(analyze(payload.title()==null ? "" : payload.title(),payload.language(),0));
    tokens.addAll(analyze(text,payload.language(),4096));
    return new Extracted(List.copyOf(tokens),text);
  }

  private List<TextToken> analyze(String text, String language, int positionBase) {
    var raw=tokenizer.tokenize(text);
    var analyzed=new com.aditya.minsearch.index.application.LanguageAwareTokenProcessor(
        new com.aditya.minsearch.index.application.EnglishStopWordFilter(),new com.aditya.minsearch.index.application.EnglishStemmer()).process(language,raw);
    var output=new java.util.ArrayList<TextToken>();
    for (var term : analyzed) {
      var surface=raw.get(term.position());
      output.add(new TextToken(term.term(),positionBase+term.position(),term.startOffset(),term.endOffset()));
      if (!surface.term().equals(term.term())) output.add(new TextToken(surface.term(),positionBase+term.position(),term.startOffset(),term.endOffset()));
    }
    return output;
  }
}
