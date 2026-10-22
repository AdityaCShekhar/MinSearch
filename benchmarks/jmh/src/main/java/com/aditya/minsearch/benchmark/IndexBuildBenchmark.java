package com.aditya.minsearch.benchmark;

import com.aditya.minsearch.index.application.IndexGenerationBuilder;
import com.aditya.minsearch.index.domain.IndexGeneration;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms512m", "-Xmx2g"})
public class IndexBuildBenchmark {
  @Param({"1000", "10000"})
  public int documents;

  @Benchmark
  public IndexGeneration buildGeneration() {
    var builder = new IndexGenerationBuilder(IndexGeneration.empty());
    for (int i = 0; i < documents; i++) builder.replace(Fixture.id(i), 1, Fixture.TOKENS);
    return builder.build(1);
  }
}
