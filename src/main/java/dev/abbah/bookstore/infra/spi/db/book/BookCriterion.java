package dev.abbah.bookstore.infra.spi.db.book;

import static org.springframework.data.core.TypedPropertyPath.path;

import dev.abbah.bookstore.domain.book.BookFilter;
import dev.abbah.bookstore.infra.spi.db.Criterion;
import dev.abbah.bookstore.infra.spi.db.CriterionBuilder;
import java.util.Optional;
import org.springframework.data.relational.core.query.Criteria;

/**
 * The filtering rules a book listing understands, one constant each. Adding a filter is one field
 * on {@link BookFilter} plus one constant here — no signature between the resource and the adapter
 * changes.
 *
 * <p>Each constant receives the whole {@link BookFilter}, so a rule may read more than one field;
 * one that does must name the other field in a comment (see {@link Criterion}).
 */
enum BookCriterion implements Criterion<BookFilter> {

  TITLE(filter ->
      CriterionBuilder.where(path(BookEntity::title)).containsIgnoreCase(filter.title())),

  AUTHOR(filter ->
      CriterionBuilder.where(path(BookEntity::author)).is(filter.author())),

  ISBN(filter ->
      CriterionBuilder.where(path(BookEntity::isbn)).is(filter.isbn()));

  private final Criterion<BookFilter> criterion;

  BookCriterion(Criterion<BookFilter> criterion) {
    this.criterion = criterion;
  }

  @Override
  public Optional<Criteria> toCriteria(BookFilter filter) {
    return criterion.toCriteria(filter);
  }

}
