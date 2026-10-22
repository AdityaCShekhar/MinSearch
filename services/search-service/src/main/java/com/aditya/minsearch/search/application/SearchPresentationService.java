package com.aditya.minsearch.search.application;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchPresentationService {
  private final JdbcTemplate jdbc;
  private final QueryLimits limits;
  private final SnippetRenderer snippets=new SnippetRenderer();
  public SearchPresentationService(JdbcTemplate jdbc, QueryLimits limits) { this.jdbc=jdbc; this.limits=limits; }
  @Transactional(readOnly=true)
  public SearchPage present(SearchPage page, String query, SearchIndexSnapshot snapshot) {
    var ast=new QueryParser(limits).parse(query);
    var evaluator=new QueryEvaluator(limits);
    var rawTerms=evaluator.scoringTerms(ast,snapshot.generation(),false);
    var englishTerms=evaluator.scoringTerms(ast,snapshot.generation(),true);
    var phrases=new LinkedHashSet<String>(); collectPhrases(ast,phrases);
    // Only page results are read; a cursor fetches one body at a time instead of retaining a page of large files.
    Map<UUID,String> rendered=new HashMap<>();
    if (!page.results().isEmpty()) {
      String placeholders=String.join(",",Collections.nCopies(page.results().size(),"(?,?)"));
      jdbc.query(connection -> {
        var statement=connection.prepareStatement("SELECT document_id,body FROM search_index_content WHERE (document_id,document_version) IN ("+placeholders+")");
        int i=1;
        for (var result : page.results()) {
          statement.setObject(i++,result.documentId()); statement.setLong(i++,snapshot.generation().document(result.documentId()).documentVersion());
        }
        statement.setFetchSize(1); return statement;
      },(org.springframework.jdbc.core.RowCallbackHandler) row -> {
        UUID id=row.getObject("document_id",UUID.class); String body=row.getString("body");
        var terms=new LinkedHashSet<String>(); boolean english=QueryEvaluator.analyzedEnglish(snapshot.documents().get(id));
        var words=java.util.regex.Pattern.compile("[\\p{L}\\p{N}\\p{M}]+").matcher(body);
        var tokenizer=new com.aditya.minsearch.index.application.TextTokenizer();
        var stemmer=new com.aditya.minsearch.index.application.EnglishStemmer();
        while (words.find() && terms.size()<1000) {
          String normalized=tokenizer.normalize(words.group());
          if ((english ? englishTerms : rawTerms).contains(english ? stemmer.stem(normalized) : normalized)) terms.add(words.group());
        }
        rendered.put(id,snippets.render(body,List.copyOf(terms),List.copyOf(phrases),SnippetRenderer.DEFAULT_LENGTH));
      });
    }
    var results=page.results().stream().map(result -> {
      var document=snapshot.documents().get(result.documentId());
      return new SearchResult(result.documentId(),result.score(),document.title(),document.language(),document.type(),
          snapshot.generation().document(result.documentId()).documentVersion(),document.uploadedAt(),rendered.getOrDefault(result.documentId(),""));
    }).toList();
    return new SearchPage(results,page.page(),page.size(),page.total(),page.generation());
  }
  private void collectPhrases(com.aditya.minsearch.search.domain.QueryNode node, Set<String> phrases) {
    if (node instanceof com.aditya.minsearch.search.domain.PhraseNode phrase) phrases.add(phrase.phrase());
    if (node instanceof com.aditya.minsearch.search.domain.BooleanNode binary) {
      collectPhrases(binary.left(),phrases);
      if (binary.operator()!=com.aditya.minsearch.search.domain.BooleanNode.Operator.NOT) collectPhrases(binary.right(),phrases);
    }
  }
}
