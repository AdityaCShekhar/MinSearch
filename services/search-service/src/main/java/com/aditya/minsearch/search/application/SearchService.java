package com.aditya.minsearch.search.application;

import com.aditya.minsearch.cache.domain.CacheStore;
import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.index.application.TextTokenizer;
import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.index.domain.Posting;
import com.aditya.minsearch.search.domain.BooleanNode;
import com.aditya.minsearch.search.domain.FuzzyNode;
import com.aditya.minsearch.search.domain.PhraseNode;
import com.aditya.minsearch.search.domain.PrefixNode;
import com.aditya.minsearch.search.domain.QueryNode;
import com.aditya.minsearch.search.domain.SearchDocument;
import com.aditya.minsearch.search.domain.TermNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class SearchService {
  private final QueryEvaluator evaluator;
  private final QueryLimits limits;
  private final RankingPolicy ranking;
  private final CacheStore cache;
  private final LongAdder cacheHits = new LongAdder();
  private final LongAdder cacheMisses = new LongAdder();

  public SearchService() {
    this.cache = new com.aditya.minsearch.cache.infrastructure.CaffeineCacheStore();
    this.limits = QueryLimits.defaults();
    this.evaluator = new QueryEvaluator(limits);
    this.ranking=RankingPolicy.defaults();
  }

  public SearchService(ObjectProvider<CacheStore> cacheProvider) {
    this(cacheProvider, QueryLimits.defaults());
  }

  public SearchService(ObjectProvider<CacheStore> cacheProvider, QueryLimits limits) {
    this(cacheProvider,limits,RankingPolicy.defaults());
  }
  @Autowired
  public SearchService(ObjectProvider<CacheStore> cacheProvider, QueryLimits limits, RankingPolicy ranking) {
    this.cache =
        cacheProvider.getIfAvailable(
            () -> new com.aditya.minsearch.cache.infrastructure.CaffeineCacheStore());
    this.limits = limits;
    this.evaluator = new QueryEvaluator(limits);
    this.ranking=ranking;
  }

  public SearchPage search(
      String query,
      IndexGeneration generation,
      Map<UUID, SearchDocument> documents,
      UUID authorizedOwner,
      int page,
      int size) {
    if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page size");
    String key =
        "search:"
            + generation.generation()
            + ":"
            + authorizedOwner
            + ":"
            + query
            + ":"
            + page
            + ":"
            + size;
    try {
      var cached = cache.get(key, SearchPage.class);
      if (cached.isPresent()) {
        cacheHits.increment();
        return cached.get();
      }
    } catch (RuntimeException cacheFailure) {
      // Cache reads are optional. Compute business work once, outside the cache failure boundary.
    }
    cacheMisses.increment();
    SearchPage computed=compute(query,generation,documents,authorizedOwner,page,size);
    try { return cache.getOrCompute(key,Duration.ofMinutes(2),() -> computed); }
    catch (RuntimeException cacheFailure) { return computed; }
  }

  public Map<String, Long> cacheStats() {
    return Map.of("hits", cacheHits.sum(), "misses", cacheMisses.sum());
  }

  private SearchPage compute(
      String query,
      IndexGeneration generation,
      Map<UUID, SearchDocument> documents,
      UUID authorizedOwner,
      int page,
      int size) {
    QueryNode ast = new QueryParser(limits).parse(query);
    var matching = evaluator.evaluate(ast, generation, documents, authorizedOwner);
    long offset = (long) page * size;
    if (offset >= matching.size())
      return new SearchPage(List.of(), page, size, matching.size(), generation.generation());
    Map<UUID, Double> scores = new HashMap<>();
    var rawTerms=evaluator.scoringTerms(ast,generation,false);
    var englishTerms=evaluator.scoringTerms(ast,generation,true);
    var scoringTerms=new java.util.HashSet<>(rawTerms); scoringTerms.addAll(englishTerms);
    for (String term : scoringTerms) {
      int df = generation.postingsFor(term).postings().size();
      double idf = Math.log((generation.documentCount() + 1.0) / (df + 1.0)) + 1.0;
      for (Posting posting : generation.postingsFor(term).postings()) {
        var metadata=documents.get(posting.documentId());
        if (!matching.contains(posting.documentId()) || !(QueryEvaluator.analyzedEnglish(metadata) ? englishTerms : rawTerms).contains(term)) continue;
        int titleFrequency=0;
        for (int p=0;p<posting.termFrequency();p++) if (posting.positionAt(p)<metadata.bodyStart()) titleFrequency++;
        int bodyFrequency=posting.termFrequency()-titleFrequency;
        double score=(bodyFrequency==0 ? 0 : 1+Math.log(bodyFrequency))*idf
            +ranking.titleBoost()*(titleFrequency==0 ? 0 : 1+Math.log(titleFrequency))*idf;
        scores.merge(posting.documentId(),score,Double::sum);
      }
    }
    for (String term : new java.util.HashSet<>(terms(ast))) {
      for (Posting posting : generation.postingsFor(new TextTokenizer().normalize(term)).postings())
        if (matching.contains(posting.documentId())) scores.merge(posting.documentId(),ranking.exactTermBoost(),Double::sum);
    }
    for (PhraseNode phrase : phrases(ast)) {
      for (UUID id : evaluator.evaluate(phrase,generation,documents,authorizedOwner))
        if (matching.contains(id)) scores.merge(id,ranking.phraseBoost(),Double::sum);
    }
    Comparator<SearchResult> order =
        Comparator.comparingDouble(SearchResult::score)
            .reversed()
            .thenComparing(
                Comparator.comparing(
                        (SearchResult result) -> documents.get(result.documentId()).uploadedAt())
                    .reversed())
            .thenComparing(SearchResult::documentId);
    int limit = (int) Math.min(offset + size, matching.size());
    var best = new java.util.PriorityQueue<SearchResult>(Math.min(limit, 128), order.reversed());
    for (UUID id : matching) {
      var result = new SearchResult(id, scores.getOrDefault(id, 0.0));
      if (best.size() < limit) best.add(result);
      else if (order.compare(result, best.peek()) < 0) {
        best.poll();
        best.add(result);
      }
    }
    List<SearchResult> ordered = new ArrayList<>(best);
    ordered.sort(order);
    int from = (int) offset;
    int to = Math.min(from + size, ordered.size());
    return new SearchPage(
        List.copyOf(ordered.subList(from, to)),
        page,
        size,
        matching.size(),
        generation.generation());
  }

  private List<String> terms(QueryNode node) {
    if (node instanceof TermNode term) return List.of(term.term());
    if (node instanceof PrefixNode prefix) return List.of(prefix.prefix());
    if (node instanceof FuzzyNode fuzzy) return List.of(fuzzy.term());
    if (node instanceof PhraseNode phrase)
      return new TextTokenizer().tokenize(phrase.phrase()).stream().map(TextToken::term).toList();
    if (node instanceof BooleanNode binary) {
      List<String> terms = new ArrayList<>(terms(binary.left()));
      if (binary.operator()!=BooleanNode.Operator.NOT) terms.addAll(terms(binary.right()));
      return terms;
    }
    return List.of();
  }
  private List<PhraseNode> phrases(QueryNode node) {
    if (node instanceof PhraseNode phrase) return List.of(phrase);
    if (node instanceof BooleanNode binary) {
      var out=new ArrayList<>(phrases(binary.left()));
      if (binary.operator()!=BooleanNode.Operator.NOT) out.addAll(phrases(binary.right()));
      return out;
    }
    return List.of();
  }
}
