package com.aditya.minsearch.benchmark;

import com.aditya.minsearch.index.application.IndexGenerationBuilder;
import com.aditya.minsearch.index.domain.IndexGeneration;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/** Cost of publishing one mutation against an already populated immutable index. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms512m", "-Xmx2g"})
public class IndexMutationBenchmark {
  @Param({"1000", "10000", "100000"})
  public int documents;

  private IndexGeneration source;

  @Setup(Level.Trial)
  public void setup() {
    var builder = new IndexGenerationBuilder(IndexGeneration.empty());
    for (int i = 0; i < documents; i++) {
      var tokens = new java.util.ArrayList<>(Fixture.TOKENS);
      tokens.add(new com.aditya.minsearch.index.application.TextToken("unique" + i, 1000, 0, 1));
      builder.replace(Fixture.id(i), 1, tokens);
    }
    source = builder.build(1);
  }

  @Benchmark
  public IndexGeneration insert() {
    return new IndexGenerationBuilder(source)
        .replace(Fixture.id(documents), 1, Fixture.TOKENS)
        .build(2);
  }

  @Benchmark
  public IndexGeneration replace() {
    return new IndexGenerationBuilder(source).replace(Fixture.id(0), 2, Fixture.TOKENS).build(2);
  }

  @Benchmark
  public IndexGeneration delete() {
    return new IndexGenerationBuilder(source).delete(Fixture.id(0), 2).build(2);
  }
}
