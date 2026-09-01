package dev.abbah.bookstore.infra.spi.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.core.TypedPropertyPath.path;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.data.relational.core.query.Criteria;

/**
 * Covers the fold that turns a set of {@link Criterion} into one {@link Criteria}: which criteria
 * survive it, and that an all-absent filter yields {@link Criteria#empty()} rather than a criteria
 * matching nothing.
 */
class CriterionCriteriaBuilderTest {

  /** Stand-in filter: the fold is domain-agnostic, so the test brings its own type. */
  private record Sample(@Nullable String title, @Nullable String author) {
  }

  /** Stand-in for {@code BookCriterion}: an enum whose constants are the criteria. */
  private enum SampleCriterion implements Criterion<Sample> {

    TITLE(filter -> CriterionBuilder.where(path(Sample::title)).is(filter.title())),

    AUTHOR(filter -> CriterionBuilder.where(path(Sample::author)).is(filter.author()));

    private final Criterion<Sample> criterion;

    SampleCriterion(Criterion<Sample> criterion) {
      this.criterion = criterion;
    }

    @Override
    public Optional<Criteria> toCriteria(Sample filter) {
      return criterion.toCriteria(filter);
    }

  }

  @Test
  void theEnumOverloadCombinesEveryApplyingConstantConjunctively() {
    Criteria criteria = CriterionCriteriaBuilder.build(
        new Sample("Dune", "Herbert"), SampleCriterion.class);

    assertThat(criteria).isEqualTo(
        Criteria.where("title").is("Dune").and(Criteria.where("author").is("Herbert")));
  }

  /** Criteria.empty() is the identity of the fold, and the only thing that produces it. */
  @Test
  void theFoldIsEmptyWhenNoCriterionApplies() {
    Criteria criteria = CriterionCriteriaBuilder.build(
        new Sample(null, null), SampleCriterion.class);

    assertThat(criteria.isEmpty()).isTrue();
  }

  /** A lone criterion is returned as-is, not wrapped in a one-sided conjunction. */
  @Test
  void aSingleApplyingCriterionIsReturnedAlone() {
    Criteria criteria = CriterionCriteriaBuilder.build(
        new Sample("Dune", null), SampleCriterion.class);

    assertThat(criteria).isEqualTo(Criteria.where("title").is("Dune"));
  }

  /** The varargs overload folds only what it is handed, whatever else the filter carries. */
  @Test
  void theVarargsOverloadFoldsOnlyTheCriteriaPassed() {
    Criteria criteria = CriterionCriteriaBuilder.build(
        new Sample("Dune", "Herbert"), SampleCriterion.AUTHOR);

    assertThat(criteria).isEqualTo(Criteria.where("author").is("Herbert"));
  }

}
