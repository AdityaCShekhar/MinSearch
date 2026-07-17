package com.aditya.minsearch.shared.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

class DatabaseMigrationTest extends PostgresRepositoryTest {

  @Autowired private JdbcClient jdbcClient;

  @Test
  @Transactional
  void migrationsAndRepositoryAccessWorkAgainstPostgres() {
    UUID id = UUID.randomUUID();

    jdbcClient
        .sql("INSERT INTO repository_test_records (id, value) VALUES (:id, :value)")
        .param("id", id)
        .param("value", "persisted")
        .update();

    String value =
        jdbcClient
            .sql("SELECT value FROM repository_test_records WHERE id = :id")
            .param("id", id)
            .query(String.class)
            .single();
    Integer migrationCount =
        jdbcClient
            .sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success")
            .query(Integer.class)
            .single();

    assertThat(value).isEqualTo("persisted");
    assertThat(migrationCount).isEqualTo(3);
  }
}
