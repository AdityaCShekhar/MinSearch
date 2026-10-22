package com.aditya.minsearch.benchmark;

import com.aditya.minsearch.autocomplete.application.AutocompleteService;
import com.aditya.minsearch.autocomplete.application.SpellingSuggestionService;
import com.aditya.minsearch.cache.domain.CacheStore;
import com.aditya.minsearch.index.application.IndexGenerationBuilder;
import com.aditya.minsearch.index.application.TextTokenizer;
import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.search.application.*;
import com.aditya.minsearch.search.domain.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms512m", "-Xmx2g"})
public class SearchAlgorithmsBenchmark {
  @Param({"1000", "10000"})
  public int documents;

  private IndexGeneration generation;
  private Map<UUID, SearchDocument> metadata;
  private SearchService uncached;
  private SearchService cached;
  private final QueryEvaluator evaluator = new QueryEvaluator();
  private final TextTokenizer tokenizer = new TextTokenizer();
  private final AutocompleteService autocomplete = new AutocompleteService(new NoCacheStore());
  private final SpellingSuggestionService suggestions = new SpellingSuggestionService();
  private QueryNode phrase;
  private QueryNode intersection;

  @Setup(Level.Trial)
  public void setup() {
    var builder = new IndexGenerationBuilder(IndexGeneration.empty());
    var docs = new HashMap<UUID, SearchDocument>();
    for (int i = 0; i < documents; i++) {
      UUID id = Fixture.id(i);
      builder.replace(id, 1, Fixture.TOKENS);
      docs.put(id, new SearchDocument(id, Fixture.OWNER, "en", "txt", Instant.EPOCH));
    }
    generation = builder.build(1);
    metadata = Map.copyOf(docs);
    var beans = new StaticListableBeanFactory(Map.of("cache", new NoCacheStore()));
    uncached = new SearchService(beans.getBeanProvider(CacheStore.class));
    cached = new SearchService();
    cached.search("distributed", generation, metadata, Fixture.OWNER, 0, 20);
    phrase = new QueryParser().parse("\"distributed systems\"");
    intersection = new QueryParser().parse("distributed AND storage");
  }

  @Benchmark
  public Object tokenize() {
    return tokenizer.tokenize(Fixture.TEXT);
  }

  @Benchmark
  public Object postingIntersection() {
    return evaluator.evaluate(intersection, generation, metadata, Fixture.OWNER);
  }

  @Benchmark
  public Object phraseRetrieval() {
    return evaluator.evaluate(phrase, generation, metadata, Fixture.OWNER);
  }

  @Benchmark
  public Object rankingUncached() {
    return uncached.search("distributed", generation, metadata, Fixture.OWNER, 0, 20);
  }

  @Benchmark
  public Object rankingCached() {
    return cached.search("distributed", generation, metadata, Fixture.OWNER, 0, 20);
  }

  @Benchmark
  public Object trieAutocompleteUncached() {
    return autocomplete.complete("sto", generation, 10);
  }

  @Benchmark
  public Object spellingSuggestions() {
    return suggestions.suggest("storag", generation);
  }
}
