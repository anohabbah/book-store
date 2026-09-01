package dev.abbah.bookstore.domain.book;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BookFilterTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "\t", "\n "})
  void blankOrNullValuesNormalizeToNull(String value) {
    BookFilter filter = new BookFilter(value, value, value);

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
