package dev.abbah.bookstore.infra.api.rest.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Request body for creating and replacing a book — the two operations share the shape. */
public record CreateBookRequest(
    @NotBlank @Size(max = 20) String isbn,
    @NotBlank @Size(max = 512) String title,
    @NotBlank @Size(max = 256) String author,
    @Nullable Integer publishedYear,
    @Nullable @Size(max = 128) String genre) {
}
