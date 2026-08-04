package com.aditya.minsearch.search.application;

import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.index.domain.Posting;
import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.index.application.TextTokenizer;
import com.aditya.minsearch.search.domain.BooleanNode;
import com.aditya.minsearch.search.domain.FuzzyNode;
import com.aditya.minsearch.search.domain.PhraseNode;
import com.aditya.minsearch.search.domain.PrefixNode;
import com.aditya.minsearch.search.domain.QueryNode;
import com.aditya.minsearch.search.domain.SearchDocument;
import com.aditya.minsearch.search.domain.TermNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class SearchService {
  private final QueryParser parser = new QueryParser();
  private final QueryEvaluator evaluator = new QueryEvaluator();

  public SearchPage search(String query, IndexGeneration generation, Map<UUID, SearchDocument> documents,
      UUID authorizedOwner, int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page size");
    QueryNode ast = parser.parse(query);
    var matching = evaluator.evaluate(ast, generation, documents, authorizedOwner);
    Map<UUID, Double> scores = new HashMap<>();
    for (String term : terms(ast)) {
      String normalized = new TextTokenizer().normalize(term);
      int df = generation.postingsFor(normalized).postings().size();
      double idf = Math.log((generation.documentCount() + 1.0) / (df + 1.0)) + 1.0;
      for (Posting posting : generation.postingsFor(normalized).postings()) {
        if (matching.contains(posting.documentId())) scores.merge(posting.documentId(), posting.termFrequency() * idf, Double::sum);
      }
    }
    List<SearchResult> ordered = new ArrayList<>();
    matching.forEach(id -> ordered.add(new SearchResult(id, scores.getOrDefault(id, 0.0))));
    ordered.sort(Comparator.comparingDouble(SearchResult::score).reversed()
        .thenComparing(Comparator.comparing((SearchResult result) -> documents.get(result.documentId()).uploadedAt()).reversed())
        .thenComparing(SearchResult::documentId));
    int from = Math.min(page * size, ordered.size());
    int to = Math.min(from + size, ordered.size());
    return new SearchPage(List.copyOf(ordered.subList(from, to)), page, size, ordered.size(), generation.generation());
  }

  private List<String> terms(QueryNode node) {
    if (node instanceof TermNode term) return List.of(term.term());
    if (node instanceof PrefixNode prefix) return List.of(prefix.prefix());
    if (node instanceof FuzzyNode fuzzy) return List.of(fuzzy.term());
    if (node instanceof PhraseNode phrase) return new TextTokenizer().tokenize(phrase.phrase()).stream().map(TextToken::term).toList();
    if (node instanceof BooleanNode binary) {
      List<String> terms = new ArrayList<>(terms(binary.left())); terms.addAll(terms(binary.right())); return terms;
    }
    return List.of();
  }
}
