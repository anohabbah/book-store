package dev.abbah.bookstore.infra.api.rest.book;

import org.jspecify.annotations.Nullable;

/** A response only ever describes a persisted book, so its id is non-null (design D3). */
public record BookDto(
    Long id,
    String isbn,
    String title,
    String author,
    @Nullable Integer publishedYear,
    @Nullable String genre) {
}
