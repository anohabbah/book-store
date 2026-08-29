package dev.abbah.bookstore.infra.spi.db.book;

import static org.assertj.core.api.Assertions.assertThat;

import dev.abbah.bookstore.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookSchemaIT {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void flywayAppliedCreateBooksTableMigration() {
    Integer applied = jdbcTemplate.queryForObject(
        "select count(*) from flyway_schema_history where version = '1' and success",
        Integer.class);

    assertThat(applied).isEqualTo(1);
  }

  @Test
  void booksTableExists() {
    Integer tables = jdbcTemplate.queryForObject(
        "select count(*) from information_schema.tables where table_name = 'books'",
        Integer.class);

    assertThat(tables).isEqualTo(1);
  }

}
