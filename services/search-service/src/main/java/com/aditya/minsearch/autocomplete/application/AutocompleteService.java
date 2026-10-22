package com.aditya.minsearch.autocomplete.application;

import com.aditya.minsearch.cache.domain.CacheStore;
import com.aditya.minsearch.cache.infrastructure.CaffeineCacheStore;
import com.aditya.minsearch.index.application.TextTokenizer;
import com.aditya.minsearch.index.domain.IndexGeneration;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public final class AutocompleteService {
  private record PopularQuery(UUID owner, String query) {}

  private final java.util.concurrent.ConcurrentMap<PopularQuery, Long> queryPopularity =
      com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(10_000)
          .expireAfterAccess(Duration.ofHours(24)).<PopularQuery, Long>build().asMap();
  private final java.util.concurrent.atomic.AtomicLong popularityRevision =
      new java.util.concurrent.atomic.AtomicLong();
  private final CacheStore cache;

  public AutocompleteService() { this(new CaffeineCacheStore()); }

  @org.springframework.beans.factory.annotation.Autowired
  public AutocompleteService(CacheStore cache) { this.cache = Objects.requireNonNull(cache); }

  public void recordQuery(String query) {
    recordQuery(query, null);
  }

  public void recordQuery(String query, UUID owner) {
    if (query == null || query.length() > 2048) return;
    String normalized = normalize(query);
    if (!normalized.isBlank()) {
      queryPopularity.merge(new PopularQuery(owner, normalized), 1L, Long::sum);
      popularityRevision.incrementAndGet();
    }
  }

  public List<String> complete(String prefix, IndexGeneration generation, int limit) {
    return complete(prefix, generation, limit, null);
  }

  @SuppressWarnings("unchecked")
  public List<String> complete(String prefix, IndexGeneration generation, int limit, UUID owner) {
    if (prefix != null && prefix.length() > 256) throw new IllegalArgumentException("Autocomplete prefix limit exceeded");
    String p = normalize(prefix);
    if (p.isBlank()) return List.of();
    String key =
        "autocomplete:"
            + generation.generation()
            + ":"
            + owner
            + ":"
            + popularityRevision.get()
            + ":"
            + p
            + ":"
            + limit;
    try {
      return cache.getOrCompute(
          key, List.class, Duration.ofMinutes(5), () -> compute(p, generation, limit, owner));
    } catch (RuntimeException cacheFailure) {
      return compute(p, generation, limit, owner);
    }
  }

  private List<String> compute(String p, IndexGeneration generation, int limit, UUID owner) {
    Map<String, Long> counts = new HashMap<>();
    queryPopularity.forEach(
        (query, count) -> {
          if (Objects.equals(query.owner(), owner)) counts.put(query.query(), count);
        });
    generation
        .postings()
        .forEach(
            (term, postings) -> counts.merge(term, (long) postings.postings().size(), Math::max));
    Trie trie = new Trie();
    counts.keySet().forEach(trie::insert);
    return trie.complete(p).stream()
        .sorted(
            Comparator.<String>comparingLong(s -> counts.getOrDefault(s, 0L))
                .reversed()
                .thenComparing(s -> s))
        .limit(Math.min(10, Math.max(1, limit)))
        .toList();
  }

  private static String normalize(String value) {
    return String.join(
        " ",
        new TextTokenizer()
            .tokenize(value == null ? "" : value).stream().map(t -> t.term()).toList());
  }

  private static final class Trie {
    private static final class Node {
      final Map<Character, Node> children = new TreeMap<>();
      boolean terminal;
    }

    private final Node root = new Node();

    void insert(String word) {
      Node n = root;
      for (char c : word.toCharArray()) n = n.children.computeIfAbsent(c, ignored -> new Node());
      n.terminal = true;
    }

    List<String> complete(String prefix) {
      Node n = root;
      for (char c : prefix.toCharArray()) {
        n = n.children.get(c);
        if (n == null) return List.of();
      }
      List<String> out = new ArrayList<>();
      record Entry(Node node, String word) {}
      var pending = new ArrayDeque<Entry>();
      pending.push(new Entry(n, prefix));
      while (!pending.isEmpty()) {
        var entry = pending.pop();
        if (entry.node().terminal) out.add(entry.word());
        entry.node().children.forEach((c, child) -> pending.push(new Entry(child, entry.word() + c)));
      }
      return out;
    }

  }
}
