package dev.abbah.bookstore.infra.spi.db;

import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.relational.core.query.Criteria;

/**
 * Folds a set of {@link Criterion} into the one {@link Criteria} a query is built from, keeping
 * only the criteria that apply.
 *
 * <p>{@link Criteria#empty()} is the identity of that fold and belongs here alone; a criterion
 * that does not apply says so with {@link Optional#empty()} (see {@link Criterion}).
 *
 * <p>Example usage:
 *
 * <pre class="code">
 *   CriterionCriteriaBuilder.build(filter, BookCriterion.class);
 * </pre>
 *
 * <p>Or:
 *
 * <pre class="code">
 *   CriterionCriteriaBuilder.build(filter, BookCriterion.TITLE, BookCriterion.AUTHOR);
 * </pre>
 */
public final class CriterionCriteriaBuilder {

  private CriterionCriteriaBuilder() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Folds every constant of an enum that implements {@link Criterion}, in declaration order.
   *
   * <p>{@code E} is bound to both {@link Enum} and {@link Criterion} because either alone is too
   * weak: {@code Enum} names the self type, not the interface, so only the intersection lets the
   * constants be read as criteria — and it ties {@code F} to the enum, so the filter passed is
   * checked against the criteria that will read it.
   *
   * @param <F> the filter type the enum's criteria read
   * @param <E> the enum type supplying the criteria
   */
  public static <F, E extends Enum<E> & Criterion<F>> Criteria build(
      F filter, Class<E> criterionEnumClazz) {
    return build(filter, criterionEnumClazz.getEnumConstants());
  }

  /** Safe: the varargs array is only read from, never stored or published. */
  @SafeVarargs
  public static <F> Criteria build(F filter, Criterion<F>... criterionEnums) {
    return Stream.of(criterionEnums)
                 .map(criterion -> criterion.toCriteria(filter))
                 .flatMap(Optional::stream)
                 .reduce(Criteria::and)
                 .orElseGet(Criteria::empty);
  }

}
