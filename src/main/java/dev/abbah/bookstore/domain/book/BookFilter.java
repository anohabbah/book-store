package dev.abbah.bookstore.domain.book;

import org.jspecify.annotations.Nullable;

/**
 * What to filter a book listing by. Every component is {@code @Nullable} because each filter is
 * independently optional; a {@code null} component means "do not filter by this".
 *
 * <p>Blank and whitespace-only values normalize to {@code null} here, once, so no filtering rule
 * downstream has to distinguish "absent" from "empty". Without it {@code ?genre=} would filter on
 * the empty string and match nothing, rather than reading as "unfiltered".
 */
public record BookFilter(
    @Nullable String title,
    @Nullable String author,
    @Nullable String isbn) {

  public BookFilter {
    title = blankToNull(title);
    author = blankToNull(author);
    isbn = blankToNull(isbn);
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }

}
