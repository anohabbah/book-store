package dev.abbah.bookstore.infra.spi.db;

import java.util.Collection;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.relational.core.query.Criteria;

/**
 * Builds one optional {@link Criteria} for a column, absent when the value to filter by is.
 *
 * <p>The column comes from a {@link TypedPropertyPath} — {@code where(path(BookEntity::title))} —
 * so renaming the accessor moves the column with it instead of leaving a stale string literal.
 *
 * <p>The operations exist because Spring Data's own vocabulary is awkward: there is no
 * {@code containsIgnoreCase}, only {@code like("%v%")} followed by {@code ignoreCase(true)} on the
 * resulting criteria. Naming that idiom once is the point of this type.
 *
 * @param <P> the property type, so the value filtered by has to match the column
 */
public final class CriterionBuilder<P> {

  private final Criteria.CriteriaStep step;

  private CriterionBuilder(Criteria.CriteriaStep step) {
    this.step = step;
  }

  public static <T, P> CriterionBuilder<P> where(TypedPropertyPath<T, P> path) {
    return new CriterionBuilder<>(Criteria.where(path));
  }

  public Optional<Criteria> is(@Nullable P value) {
    return value == null ? Optional.empty() : Optional.of(step.is(value));
  }

  /** Case-insensitive substring match. Only meaningful on a text column. */
  public Optional<Criteria> containsIgnoreCase(@Nullable String value) {
    return value == null
        ? Optional.empty()
        : Optional.of(step.like("%" + value + "%").ignoreCase(true));
  }

  /** Absent for an empty collection too — {@code IN ()} is not valid SQL. */
  public Optional<Criteria> in(@Nullable Collection<? extends P> values) {
    return values == null || values.isEmpty() ? Optional.empty() : Optional.of(step.in(values));
  }

  public Optional<Criteria> gte(@Nullable P value) {
    return value == null ? Optional.empty() : Optional.of(step.greaterThanOrEquals(value));
  }

  public Optional<Criteria> lte(@Nullable P value) {
    return value == null ? Optional.empty() : Optional.of(step.lessThanOrEquals(value));
  }

  /**
   * Absent unless <em>both</em> bounds are present. A half-open range is
   * {@link #gte} or {@link #lte}, spelled out by the caller rather than guessed at here.
   */
  public Optional<Criteria> between(@Nullable P begin, @Nullable P end) {
    return begin == null || end == null ? Optional.empty() : Optional.of(step.between(begin, end));
  }

}
