package com.aditya.minsearch.autocomplete.application;

import com.aditya.minsearch.index.application.TextTokenizer;
import com.aditya.minsearch.index.domain.IndexGeneration;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public final class SpellingSuggestionService {
  public Optional<String> suggest(String input, IndexGeneration generation) {
    if (input != null && input.length() > 256) throw new IllegalArgumentException("Suggestion term limit exceeded");
    String term = new TextTokenizer().normalize(input == null ? "" : input);
    if (term.isBlank()) return Optional.empty();
    if (generation.postings().containsKey(term)) return Optional.of(term);
    int max = term.length() < 5 ? 1 : 2;
    return generation.postings().entrySet().stream().filter(e -> Math.abs(e.getKey().length()-term.length()) <= max)
        .sorted(Comparator.comparing(e -> e.getKey())).limit(5000)
        .map(e -> new Candidate(e.getKey(), distance(term, e.getKey()), e.getValue().postings().size()))
        .filter(c -> c.distance <= max).sorted(Comparator.comparingInt(Candidate::distance).thenComparing(Comparator.comparingInt(Candidate::frequency).reversed()).thenComparing(Candidate::term))
        .filter(c -> c.distance == 1 || c.frequency >= 2).map(Candidate::term).findFirst();
  }
  private record Candidate(String term, int distance, int frequency) {}
  static int distance(String a, String b) { int[] prev = new int[b.length()+1]; for(int j=0;j<=b.length();j++)prev[j]=j; for(int i=1;i<=a.length();i++){int[] cur=new int[b.length()+1];cur[0]=i;for(int j=1;j<=b.length();j++)cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));prev=cur;}return prev[b.length()]; }
}
