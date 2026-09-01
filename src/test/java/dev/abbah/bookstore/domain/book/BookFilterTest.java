package dev.abbah.bookstore.domain.book;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BookFilterTest {

  @Test
  void blankValuesNormalizeToNull() {
    BookFilter filter = new BookFilter("", "", "");

    assertThat(filter.title()).isNull();
    assertThat(filter.author()).isNull();
    assertThat(filter.isbn()).isNull();
  }

  @Test
  void whitespaceOnlyValuesNormalizeToNull() {
    BookFilter filter = new BookFilter("   ", "\t", "\n ");

    assertThat(filter.title()).isNull();
    assertThat(filter.author()).isNull();
    assertThat(filter.isbn()).isNull();
  }

  @Test
  void nullValuesStayNull() {
    BookFilter filter = new BookFilter(null, null, null);

    assertThat(filter.title()).isNull();
    assertThat(filter.author()).isNull();
    assertThat(filter.isbn()).isNull();
  }

  @Test
  void nonBlankValuesPassThroughUntouched() {
    BookFilter filter = new BookFilter("  Dune  ", "Frank Herbert", "978-0441013593");

    assertThat(filter.title()).isEqualTo("  Dune  ");
    assertThat(filter.author()).isEqualTo("Frank Herbert");
    assertThat(filter.isbn()).isEqualTo("978-0441013593");
  }

}
