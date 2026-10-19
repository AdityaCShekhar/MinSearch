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

  private final Map<PopularQuery, Long> queryPopularity =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicLong popularityRevision =
      new java.util.concurrent.atomic.AtomicLong();
  private final CacheStore cache = new CaffeineCacheStore();

  public void recordQuery(String query) {
    recordQuery(query, null);
  }

  public void recordQuery(String query, UUID owner) {
    String normalized = normalize(query);
    if (!normalized.isBlank()) {
      queryPopularity.merge(new PopularQuery(owner, normalized), 1L, Long::sum);
      popularityRevision.incrementAndGet();
    }
  }

  public List<String> complete(String prefix, IndexGeneration generation, int limit) {
    return complete(prefix, generation, limit, null);
  }

  public List<String> complete(String prefix, IndexGeneration generation, int limit, UUID owner) {
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
          key, Duration.ofMinutes(5), () -> compute(p, generation, limit, owner));
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
      collect(n, new StringBuilder(prefix), out);
      return out;
    }

    void collect(Node n, StringBuilder b, List<String> out) {
      if (n.terminal) out.add(b.toString());
      n.children.forEach(
          (c, child) -> {
            b.append(c);
            collect(child, b, out);
            b.setLength(b.length() - 1);
          });
    }
  }
}
