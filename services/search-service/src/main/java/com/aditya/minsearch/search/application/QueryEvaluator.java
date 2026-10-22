package com.aditya.minsearch.search.application;

import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.index.application.TextTokenizer;
import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.index.domain.Posting;
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
  private final QueryLimits limits;
  public QueryEvaluator() { this(QueryLimits.defaults()); }
  public QueryEvaluator(QueryLimits limits) { this.limits = limits; }
  private static final int MAX_PREFIX_EXPANSIONS = 100;
  private static final int MAX_FUZZY_EXPANSIONS = 50;
  private static final int MAX_FUZZY_DISTANCE = 2;
  private final TextTokenizer tokenizer = new TextTokenizer();

  public Set<UUID> evaluate(
      QueryNode query,
      IndexGeneration generation,
      Map<UUID, SearchDocument> documents,
      UUID authorizedOwner) {
    if (query instanceof NotNode)
      throw new QuerySyntaxException("Standalone NOT is not supported", 0);
    QueryNode analyzed=analyze(query);
    Set<UUID> result;
    if (java.util.Objects.equals(analyzed,query) && !hasPhrase(query)) result=evaluate(query,generation,documents,documents.keySet(),false);
    else {
      result=evaluate(query,generation,documents,documents.keySet(),false);
      result.removeIf(id -> analyzedEnglish(documents.get(id)));
      if (analyzed!=null) {
        var english=evaluate(analyzed,generation,documents,documents.keySet(),true);
        english.removeIf(id -> !analyzedEnglish(documents.get(id)));
        result.addAll(english);
      }
    }
    result.removeIf(
        id -> {
          SearchDocument document = documents.get(id);
          return document == null
              || (authorizedOwner != null && !authorizedOwner.equals(document.ownerId()));
        });
    return result;
  }

  private Set<UUID> evaluate(
      QueryNode node,
      IndexGeneration generation,
      Map<UUID, SearchDocument> documents,
      Set<UUID> universe, boolean english) {
    if (node instanceof TermNode term)
      return postings(generation, tokenizer.normalize(term.term()));
    if (node instanceof PrefixNode prefix)
      return expansions(generation, tokenizer.normalize(prefix.prefix()), true);
    if (node instanceof FuzzyNode fuzzy)
      return expansions(generation, tokenizer.normalize(fuzzy.term()), false);
    if (node instanceof PhraseNode phrase)
      return phrase(generation, phraseTokens(phrase.phrase(),english));
    if (node instanceof FilterNode filter) return filter(filter, documents);
    if (node instanceof NotNode not) {
      Set<UUID> result = new HashSet<>(universe);
      result.removeAll(evaluate(not.child(), generation, documents, universe,english));
      return result;
    }
    BooleanNode binary = (BooleanNode) node;
    Set<UUID> left = evaluate(binary.left(), generation, documents, universe,english);
    Set<UUID> right = evaluate(binary.right(), generation, documents, universe,english);
    if (binary.operator() == BooleanNode.Operator.OR) {
      left.addAll(right);
      return left;
    }
    if (binary.operator() == BooleanNode.Operator.NOT) {
      left.removeAll(right);
      return left;
    }
    left.retainAll(right);
    return left;
  }

  private Set<UUID> postings(IndexGeneration generation, String term) {
    Set<UUID> result = new HashSet<>();
    if (generation.postings().containsKey(term)) {
      for (Posting posting : generation.postingsFor(term).postings())
        result.add(posting.documentId());
    }
    return result;
  }

  static boolean analyzedEnglish(SearchDocument document) {
    return document!=null && document.bodyStart()>0 && document.language()!=null
        && document.language().toLowerCase(java.util.Locale.ROOT).startsWith("en");
  }
  private List<TextToken> phraseTokens(String text, boolean english) {
    var tokens=tokenizer.tokenize(text);
    return english ? new com.aditya.minsearch.index.application.LanguageAwareTokenProcessor(
        new com.aditya.minsearch.index.application.EnglishStopWordFilter(),new com.aditya.minsearch.index.application.EnglishStemmer()).process("en",tokens) : tokens;
  }
  private QueryNode analyze(QueryNode node) {
    if (node instanceof TermNode term) {
      var tokens=phraseTokens(term.term(),true);
      return tokens.isEmpty() ? null : new TermNode(tokens.get(0).term());
    }
    if (node instanceof PhraseNode phrase) return phraseTokens(phrase.phrase(),true).isEmpty() ? null : node;
    if (node instanceof BooleanNode binary) {
      var left=analyze(binary.left()); var right=analyze(binary.right());
      if (left==null) return binary.operator()==BooleanNode.Operator.NOT ? null : right;
      if (right==null) return left;
      return new BooleanNode(left,binary.operator(),right);
    }
    return node;
  }
  private boolean hasPhrase(QueryNode node) {
    return node instanceof PhraseNode || (node instanceof BooleanNode binary && (hasPhrase(binary.left()) || hasPhrase(binary.right())));
  }

  public Set<String> scoringTerms(QueryNode node, IndexGeneration generation, boolean english) {
    if (english) { node=analyze(node); if (node==null) return Set.of(); }
    var terms=new java.util.LinkedHashSet<String>();
    collectTerms(node,generation,english,terms);
    return terms;
  }
  private void collectTerms(QueryNode node, IndexGeneration generation, boolean english, Set<String> terms) {
    if (node instanceof TermNode term) terms.add(tokenizer.normalize(term.term()));
    else if (node instanceof PhraseNode phrase) phraseTokens(phrase.phrase(),english).forEach(token -> terms.add(token.term()));
    else if (node instanceof PrefixNode prefix) terms.addAll(expansionTerms(generation,tokenizer.normalize(prefix.prefix()),true));
    else if (node instanceof FuzzyNode fuzzy) terms.addAll(expansionTerms(generation,tokenizer.normalize(fuzzy.term()),false));
    else if (node instanceof BooleanNode binary) {
      collectTerms(binary.left(),generation,english,terms);
      if (binary.operator()!=BooleanNode.Operator.NOT) collectTerms(binary.right(),generation,english,terms);
    }
  }
  private List<String> expansionTerms(IndexGeneration generation, String value, boolean prefix) {
    var out=new java.util.ArrayList<String>();
    var vocabulary=generation.vocabulary();
    int first=prefix ? java.util.Collections.binarySearch(vocabulary,value) : 0;
    if (first<0) first=-first-1;
    for (int i=first;i<vocabulary.size();i++) {
      String term=vocabulary.get(i);
      if (prefix && !term.startsWith(value)) break;
      if (prefix || distanceAtMost(term,value,limits.fuzzyDistance())) {
        out.add(term);
        if (out.size()>=(prefix ? limits.prefixExpansions() : limits.fuzzyExpansions())) break;
      }
    }
    return out;
  }

  private Set<UUID> expansions(IndexGeneration generation, String value, boolean prefix) {
    Set<UUID> result = new HashSet<>();
    for (String term : expansionTerms(generation,value,prefix)) result.addAll(postings(generation,term));
    return result;
  }

  private Set<UUID> phrase(IndexGeneration generation, List<TextToken> terms) {
    if (terms.isEmpty()) return new HashSet<>();
    Set<UUID> candidates = new HashSet<>(postings(generation, terms.get(0).term()));
    for (int index = 1; index < terms.size(); index++)
      candidates.retainAll(postings(generation, terms.get(index).term()));
    Set<UUID> result = new HashSet<>();
    for (UUID id : candidates) {
      boolean match = false;
      Posting first = generation.postingsFor(terms.get(0).term()).forDocument(id);
      if (first != null) {
        for (int position = 0; position < first.termFrequency(); position++) {
          int start = first.positionAt(position);
          match = true;
          for (int index = 1; index < terms.size(); index++) {
            Posting posting = generation.postingsFor(terms.get(index).term()).forDocument(id);
            if (posting == null || !posting.hasPosition(start + terms.get(index).position() - terms.get(0).position())) {
              match = false;
              break;
            }
          }
          if (match) break;
        }
      }
      if (match) result.add(id);
    }
    return result;
  }

  private Set<UUID> filter(FilterNode filter, Map<UUID, SearchDocument> documents) {
    return documents.values().stream()
        .filter(
            document ->
                switch (filter.field().toLowerCase(java.util.Locale.ROOT)) {
                  case "owner" -> document.ownerId().toString().equalsIgnoreCase(filter.value());
                  case "language" ->
                      document.language() != null
                          && document.language().equalsIgnoreCase(filter.value());
                  case "type" -> document.type().equalsIgnoreCase(filter.value());
                  case "from" ->
                      !document
                          .uploadedAt()
                          .isBefore(
                              LocalDate.parse(filter.value())
                                  .atStartOfDay(java.time.ZoneOffset.UTC)
                                  .toInstant());
                  case "to" ->
                      document
                          .uploadedAt()
                          .isBefore(
                              LocalDate.parse(filter.value())
                                  .plusDays(1)
                                  .atStartOfDay(java.time.ZoneOffset.UTC)
                                  .toInstant());
                  default -> false;
                })
        .map(SearchDocument::id)
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  private boolean distanceAtMost(String left, String right, int limit) {
    if (Math.abs(left.length() - right.length()) > limit) return false;
    int[] previous = new int[right.length() + 1];
    int[] current = new int[right.length() + 1];
    for (int index = 0; index <= right.length(); index++) previous[index] = index;
    for (int row = 1; row <= left.length(); row++) {
      current[0] = row;
      int minimum = row;
      for (int column = 1; column <= right.length(); column++) {
        current[column] =
            Math.min(
                Math.min(current[column - 1] + 1, previous[column] + 1),
                previous[column - 1] + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1));
        minimum = Math.min(minimum, current[column]);
      }
      if (minimum > limit) return false;
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[right.length()] <= limit;
  }
}
