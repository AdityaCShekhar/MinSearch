package com.aditya.minsearch.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QueryLexerTest {
  private final QueryLexer lexer = new QueryLexer();

  @Test
  void lexesTermsOperatorsPhrasesAndFilterMarkersWithOffsets() {
    var tokens = lexer.lex("spring AND \"kafka topic\" language:en spr* sprng~");

    assertThat(tokens).extracting(QueryToken::type).containsExactly(
        QueryToken.Type.TERM, QueryToken.Type.AND, QueryToken.Type.PHRASE,
        QueryToken.Type.TERM, QueryToken.Type.COLON, QueryToken.Type.TERM,
        QueryToken.Type.TERM, QueryToken.Type.STAR, QueryToken.Type.TERM, QueryToken.Type.FUZZY);
    assertThat(tokens.get(2).lexeme()).isEqualTo("kafka topic");
    assertThat(tokens.get(4).start()).isEqualTo(33);
  }

  @Test
  void recognizesBooleanKeywordsCaseInsensitivelyButKeepsTermLexemes() {
    var tokens = lexer.lex("and OR Not android");

    assertThat(tokens).extracting(QueryToken::type)
        .containsExactly(QueryToken.Type.AND, QueryToken.Type.OR, QueryToken.Type.NOT, QueryToken.Type.TERM);
    assertThat(tokens.get(3).lexeme()).isEqualTo("android");
  }

  @Test
  void reportsMalformedQueriesAtTheOffendingPosition() {
    assertThatThrownBy(() -> lexer.lex("title \"missing"))
        .isInstanceOf(QuerySyntaxException.class).hasMessage("Unterminated phrase at position 6");
    assertThatThrownBy(() -> lexer.lex("spring & kafka"))
        .isInstanceOf(QuerySyntaxException.class).hasMessage("Unexpected character '&' at position 7");
    assertThatThrownBy(() -> lexer.lex("\"\""))
        .isInstanceOf(QuerySyntaxException.class).hasMessage("Empty phrase at position 0");
  }
}
