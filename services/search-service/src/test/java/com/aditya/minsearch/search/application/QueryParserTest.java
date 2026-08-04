package com.aditya.minsearch.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aditya.minsearch.search.domain.BooleanNode;
import com.aditya.minsearch.search.domain.FilterNode;
import com.aditya.minsearch.search.domain.PhraseNode;
import com.aditya.minsearch.search.domain.QueryNode;
import com.aditya.minsearch.search.domain.TermNode;
import org.junit.jupiter.api.Test;

class QueryParserTest {
  private final QueryParser parser = new QueryParser();

  @Test
  void appliesNotAndOrPrecedenceAndImplicitAnd() {
    QueryNode query = parser.parse("spring OR boot AND kafka NOT rabbit");

    assertThat(query).isInstanceOf(BooleanNode.class);
    BooleanNode or = (BooleanNode) query;
    assertThat(or.operator()).isEqualTo(BooleanNode.Operator.OR);
    assertThat(or.right()).isInstanceOf(BooleanNode.class);
    assertThat(((BooleanNode) or.right()).operator()).isEqualTo(BooleanNode.Operator.NOT);
  }

  @Test
  void parsesParenthesesPhrasesAndFilters() {
    QueryNode query = parser.parse("(spring OR \"kafka boot\") language:en");
    assertThat(query).isInstanceOf(BooleanNode.class);
    BooleanNode and = (BooleanNode) query;
    assertThat(and.operator()).isEqualTo(BooleanNode.Operator.AND);
    assertThat(and.right()).isEqualTo(new FilterNode("language", "en"));
    assertThat(((BooleanNode) and.left()).right()).isEqualTo(new PhraseNode("kafka boot"));
  }

  @Test
  void rejectsDanglingOperatorsAndUnclosedParentheses() {
    assertThatThrownBy(() -> parser.parse("spring AND"))
        .isInstanceOf(QuerySyntaxException.class);
    assertThatThrownBy(() -> parser.parse("(spring OR boot"))
        .isInstanceOf(QuerySyntaxException.class).hasMessageContaining("Missing closing parenthesis");
  }
}
