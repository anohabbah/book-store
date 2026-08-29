package dev.abbah.bookstore.domain.book;

import org.jspecify.annotations.Nullable;

/**
 * Bibliographic record of a book the store carries. The id is {@code @Nullable} because a
 * not-yet-persisted book legitimately has none until the database assigns it.
 */
public record Book(
    @Nullable Long id,
    String isbn,
    String title,
    String author,
    @Nullable Integer publishedYear,
    @Nullable String genre) {
}
