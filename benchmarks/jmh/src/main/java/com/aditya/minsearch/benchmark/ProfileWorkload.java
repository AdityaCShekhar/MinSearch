package com.aditya.minsearch.benchmark;

import com.aditya.minsearch.cache.domain.CacheStore;
import com.aditya.minsearch.index.application.IndexGenerationBuilder;
import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.search.application.*;
import com.aditya.minsearch.search.domain.SearchDocument;
import com.aditya.minsearch.shared.domain.event.DocumentIndexPayload;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.Recording;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.json.JsonMapper;

/** Bounded real-engine CPU, allocation, retained-heap and writer-contention profiling. */
public final class ProfileWorkload {
  public static void main(String[] args) throws Exception {
    int count = Integer.parseInt(args[0]);
    int seconds = Integer.parseInt(args[1]);
    Path output = Path.of(args[2]);
    Files.createDirectories(output);
    var builder = new IndexGenerationBuilder(IndexGeneration.empty());
    var metadata = new HashMap<UUID, SearchDocument>();
    // Variable lengths include positions beyond Integer's cache; each document has a unique term.
    var sets = new ArrayList<List<TextToken>>();
    for (int length : new int[] {64, 256, 1024}) {
      var tokens = new ArrayList<TextToken>();
      for (int p = 0; p < length; p++)
        tokens.add(new TextToken(p % 2 == 0 ? "distributed" : "systems", p, p * 12, p * 12 + 11));
      sets.add(List.copyOf(tokens));
    }
    for (int i = 0; i < count; i++) {
      var tokens = new ArrayList<>(sets.get(i % 20 == 0 ? 2 : i % 4 == 0 ? 1 : 0));
      tokens.add(new TextToken("unique" + i, 2000, 24000, 24010));
      UUID id = Fixture.id(i);
      builder.replace(id, 1, tokens);
      metadata.put(id, new SearchDocument(id, Fixture.OWNER, "en", "txt", Instant.EPOCH));
    }
    IndexGeneration generation = builder.build(1);
    builder = null;
    var beans = new StaticListableBeanFactory(Map.of("cache", new NoCacheStore()));
    SearchService search = new SearchService(beans.getBeanProvider(CacheStore.class));
    String[] queries = {
      "unique0",
      "\"distributed systems\"",
      "distributed AND systems",
      "unique999*",
      "uniqe0~",
      "distributed NOT unique0"
    };
    long prefixTotal =
        Math.min(
            100,
            java.util.stream.IntStream.range(0, count)
                .filter(i -> ("unique" + i).startsWith("unique999"))
                .count());
    long[] totals = {1, count, count, prefixTotal, -1, count - 1};
    // Check deterministic operations against known totals; bounded fuzzy expansion varies by
    // vocabulary.
    for (int i = 0; i < queries.length; i++) {
      long total = search.search(queries[i], generation, metadata, Fixture.OWNER, 0, 20).total();
      if (totals[i] >= 0 && total != totals[i]) throw new AssertionError(queries[i]);
    }
    System.gc();
    Thread.sleep(100);
    long retainedHeap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    var recording = new Recording();
    recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
    recording.enable("jdk.ObjectAllocationSample").with("throttle", "150/s");
    recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1));
    recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1));
    recording.enable("jdk.GarbageCollection");
    recording.setDestination(output.resolve("profile.jfr"));
    var queriesDone = new AtomicLong();
    var mutationsDone = new AtomicLong();
    var pool = Executors.newFixedThreadPool(6);
    var jobs = new ArrayList<Future<?>>();
    IndexGeneration fixed = generation;
    var docs = Map.copyOf(metadata);
    Path root = Files.createTempDirectory("minsearch-profile-");
    String content = Fixture.TEXT;
    Files.writeString(root.resolve("profile.txt"), content);
    String checksum =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    SearchIndexStore store =
        new SearchIndexStore() {
          long revision = 1;

          public OptionalLong save(Mutation mutation) {
            return OptionalLong.of(++revision);
          }

          public List<StoredMutation> load() {
            return List.of();
          }

          public void forEach(java.util.function.Consumer<StoredMutation> consumer) {
            // Recovery is seeded separately below; this store deliberately excludes persistence
            // latency.
            for (int i = 0; i < count; i++)
              consumer.accept(
                  new StoredMutation(
                      1,
                      new Mutation(
                          Fixture.id(i),
                          Fixture.id(i),
                          1,
                          false,
                          docs.get(Fixture.id(i)),
                          Fixture.TOKENS)));
          }
        };
    DocumentIndexingService index = new DocumentIndexingService(store, root.toString());
    index.recover();
    recording.start();
    long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
    long started = System.nanoTime();
    try {
      for (int reader = 0; reader < 4; reader++) {
        final int offset = reader;
        jobs.add(
            pool.submit(
                () -> {
                  int operation = offset;
                  while (System.nanoTime() < deadline) {
                    var page =
                        search.search(
                            queries[(operation++) % queries.length],
                            fixed,
                            docs,
                            Fixture.OWNER,
                            0,
                            20);
                    if (page.total() < 0) throw new AssertionError();
                    queriesDone.incrementAndGet();
                  }
                }));
      }
      for (int writer = 0; writer < 2; writer++) {
        final UUID id = Fixture.id(writer);
        jobs.add(
            pool.submit(
                () -> {
                  long version = 2;
                  while (System.nanoTime() < deadline) {
                    try {
                      index.apply(
                          new DomainEvent(
                              UUID.randomUUID(), "document.updated", Instant.now(), "{}"),
                          new DocumentIndexPayload(
                              1,
                              id,
                              Fixture.OWNER,
                              version++,
                              "profile.txt",
                              checksum,
                              "en",
                              "TXT",
                              Instant.EPOCH));
                      mutationsDone.incrementAndGet();
                    } catch (Exception failure) {
                      throw new IllegalStateException(failure);
                    }
                  }
                }));
      }
      for (var job : jobs) job.get(seconds + 60L, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
      recording.stop();
      recording.close();
      Files.deleteIfExists(root.resolve("profile.txt"));
      Files.deleteIfExists(root);
    }
    double elapsed = (System.nanoTime() - started) / 1e9;
    Files.writeString(
        output.resolve("summary.json"),
        JsonMapper.builder()
                .build()
                .writeValueAsString(
                    Map.of(
                        "documents",
                        count,
                        "requestedSeconds",
                        seconds,
                        "elapsedSeconds",
                        elapsed,
                        "queries",
                        queriesDone.get(),
                        "mutations",
                        mutationsDone.get(),
                        "retainedHeapBytes",
                        retainedHeap,
                        "readers",
                        4,
                        "writers",
                        2,
                        "scope",
                        "Synthetic variable-length postings; uncached queries on a fixed generation"
                            + " and two synchronized real indexing writers; no database or Kafka"))
            + "\n");
  }
}
