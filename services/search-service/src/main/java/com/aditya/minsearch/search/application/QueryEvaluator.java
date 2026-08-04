package com.aditya.minsearch.search.application;

import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.index.domain.Posting;
import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.index.application.TextTokenizer;
import com.aditya.minsearch.search.domain.BooleanNode;
import com.aditya.minsearch.search.domain.FilterNode;
import com.aditya.minsearch.search.domain.FuzzyNode;
import com.aditya.minsearch.search.domain.NotNode;
import com.aditya.minsearch.search.domain.PhraseNode;
import com.aditya.minsearch.search.domain.PrefixNode;
import com.aditya.minsearch.search.domain.QueryNode;
import com.aditya.minsearch.search.domain.SearchDocument;
import com.aditya.minsearch.search.domain.TermNode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class QueryEvaluator {
  private static final int MAX_PREFIX_EXPANSIONS = 100;
  private static final int MAX_FUZZY_EXPANSIONS = 50;
  private static final int MAX_FUZZY_DISTANCE = 2;
  private final TextTokenizer tokenizer = new TextTokenizer();

  public Set<UUID> evaluate(QueryNode query, IndexGeneration generation,
      Map<UUID, SearchDocument> documents, UUID authorizedOwner) {
    Set<UUID> universe = new HashSet<>(documents.keySet());
    Map<UUID, SearchDocument> visible = documents.entrySet().stream()
        .filter(entry -> authorizedOwner == null || authorizedOwner.equals(entry.getValue().ownerId()))
        .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    universe.retainAll(visible.keySet());
    if (query instanceof NotNode) throw new QuerySyntaxException("Standalone NOT is not supported", 0);
    Set<UUID> result = evaluate(query, generation, visible, universe);
    result.retainAll(visible.keySet());
    return result;
  }

  private Set<UUID> evaluate(QueryNode node, IndexGeneration generation,
      Map<UUID, SearchDocument> documents, Set<UUID> universe) {
    if (node instanceof TermNode term) return postings(generation, tokenizer.normalize(term.term()));
    if (node instanceof PrefixNode prefix) return expansions(generation, tokenizer.normalize(prefix.prefix()), true);
    if (node instanceof FuzzyNode fuzzy) return expansions(generation, tokenizer.normalize(fuzzy.term()), false);
    if (node instanceof PhraseNode phrase) return phrase(generation, tokenizer.tokenize(phrase.phrase()));
    if (node instanceof FilterNode filter) return filter(filter, documents);
    if (node instanceof NotNode not) {
      Set<UUID> result = new HashSet<>(universe);
      result.removeAll(evaluate(not.child(), generation, documents, universe));
      return result;
    }
    BooleanNode binary = (BooleanNode) node;
    Set<UUID> left = evaluate(binary.left(), generation, documents, universe);
    Set<UUID> right = evaluate(binary.right(), generation, documents, universe);
    if (binary.operator() == BooleanNode.Operator.OR) { left.addAll(right); return left; }
    if (binary.operator() == BooleanNode.Operator.NOT) { left.removeAll(right); return left; }
    left.retainAll(right); return left;
  }

  private Set<UUID> postings(IndexGeneration generation, String term) {
    Set<UUID> result = new HashSet<>();
    if (generation.postings().containsKey(term)) {
      for (Posting posting : generation.postingsFor(term).postings()) result.add(posting.documentId());
    }
    return result;
  }

  private Set<UUID> expansions(IndexGeneration generation, String value, boolean prefix) {
    Set<UUID> result = new HashSet<>();
    int expanded = 0;
    for (String term : generation.postings().keySet().stream().sorted().toList()) {
      if ((prefix && term.startsWith(value)) || (!prefix && distanceAtMost(term, value, MAX_FUZZY_DISTANCE))) {
        result.addAll(postings(generation, term));
        if (++expanded >= (prefix ? MAX_PREFIX_EXPANSIONS : MAX_FUZZY_EXPANSIONS)) break;
      }
    }
    return result;
  }

  private Set<UUID> phrase(IndexGeneration generation, List<TextToken> terms) {
    if (terms.isEmpty()) return Set.of();
    Set<UUID> candidates = new HashSet<>(postings(generation, terms.get(0).term()));
    for (int index = 1; index < terms.size(); index++) candidates.retainAll(postings(generation, terms.get(index).term()));
    Set<UUID> result = new HashSet<>();
    for (UUID id : candidates) {
      boolean match = false;
      for (Posting first : generation.postingsFor(terms.get(0).term()).postings()) {
        if (!first.documentId().equals(id)) continue;
        for (int start : first.positions()) {
          match = true;
          for (int index = 1; index < terms.size(); index++) {
            Posting posting = generation.postingsFor(terms.get(index).term()).forDocument(id);
            if (posting == null || !posting.positions().contains(start + index)) { match = false; break; }
          }
          if (match) break;
        }
        if (match) break;
      }
      if (match) result.add(id);
    }
    return result;
  }

  private Set<UUID> filter(FilterNode filter, Map<UUID, SearchDocument> documents) {
    return documents.values().stream().filter(document -> switch (filter.field().toLowerCase()) {
      case "owner" -> document.ownerId().toString().equalsIgnoreCase(filter.value());
      case "language" -> document.language() != null && document.language().equalsIgnoreCase(filter.value());
      case "type" -> document.type().equalsIgnoreCase(filter.value());
      case "from" -> !document.uploadedAt().isBefore(LocalDate.parse(filter.value()).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
      case "to" -> document.uploadedAt().isBefore(LocalDate.parse(filter.value()).plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
      default -> false;
    }).map(SearchDocument::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  private boolean distanceAtMost(String left, String right, int limit) {
    if (Math.abs(left.length() - right.length()) > limit) return false;
    int[] previous = new int[right.length() + 1];
    for (int index = 0; index <= right.length(); index++) previous[index] = index;
    for (int row = 1; row <= left.length(); row++) {
      int[] current = new int[right.length() + 1]; current[0] = row;
      for (int column = 1; column <= right.length(); column++) {
        current[column] = Math.min(Math.min(current[column - 1] + 1, previous[column] + 1),
            previous[column - 1] + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1));
      }
      previous = current;
    }
    return previous[right.length()] <= limit;
  }
}
