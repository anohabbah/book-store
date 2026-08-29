package dev.abbah.bookstore.infra.spi.db.book;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC aggregate for the {@code books} table. The id is {@code @Nullable}
 * because it is legitimately absent until the database generates it on insert.
 */
@Table("books")
public record BookEntity(
    @Id @Nullable Long id,
    String isbn,
    String title,
    String author,
    @Nullable Integer publishedYear,
    @Nullable String genre) {
}
