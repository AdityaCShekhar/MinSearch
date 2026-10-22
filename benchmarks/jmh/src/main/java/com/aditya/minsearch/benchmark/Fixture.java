package com.aditya.minsearch.benchmark;

import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.index.application.TextTokenizer;
import java.util.List;
import java.util.UUID;

public final class Fixture {
  public static final UUID OWNER = new UUID(1, 1);
  public static final String TEXT =
      "distributed systems reliable storage search indexing transaction document ranking cache"
          + " memory analysis database query network archive science algorithm performance durable"
          + " snapshot version token distributed systems search document query memory storage"
          + " ranking network transaction analysis durable snapshot version token cache archive"
          + " science algorithm performance database indexing";
  public static final List<TextToken> TOKENS = new TextTokenizer().tokenize(TEXT);

  public static UUID id(int index) {
    return new UUID(index * 6364136223846793005L, index + 1);
  }

  private Fixture() {}
}
