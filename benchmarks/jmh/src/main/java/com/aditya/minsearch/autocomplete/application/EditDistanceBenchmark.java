package com.aditya.minsearch.autocomplete.application;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms512m", "-Xmx2g"})
public class EditDistanceBenchmark {
  @Param({"distributed", "transactional", "internationalization"})
  public String term;

  @Benchmark
  public int levenshtein() {
    return SpellingSuggestionService.distance(term, "distribution");
  }
}
