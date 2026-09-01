package dev.abbah.bookstore.infra.spi.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.core.TypedPropertyPath.path;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.data.relational.core.query.Criteria;

/**
 * Covers every {@link CriterionBuilder} operation, including the range and set operations that
 * have no production caller yet — untested they would be dead code.
 */
class CriterionBuilderTest {

  /** Stand-in aggregate: the builder is domain-agnostic, so the test uses its own type. */
  private record Sample(String title, @Nullable Integer publishedYear) {
  }

  @Test
  void isBuildsAnEqualityCriterion() {
    assertThat(CriterionBuilder.where(path(Sample::title)).is("Dune"))
        .contains(Criteria.where("title").is("Dune"));
  }

  @Test
  void containsIgnoreCaseWrapsTheValueInWildcardsAndIgnoresCase() {
    Criteria criteria = CriterionBuilder.where(path(Sample::title))
        .containsIgnoreCase("dune")
        .orElseThrow();

    assertThat(criteria.getColumn()).hasToString("title");
    assertThat(criteria.getComparator()).isEqualTo(Criteria.Comparator.LIKE);
    assertThat(criteria.getValue()).isEqualTo("%dune%");
    assertThat(criteria.isIgnoreCase()).isTrue();
  }

  @Test
  void inBuildsASetCriterion() {
    assertThat(CriterionBuilder.where(path(Sample::title)).in(List.of("Dune", "Hyperion")))
        .contains(Criteria.where("title").in(List.of("Dune", "Hyperion")));
  }

  @Test
  void gteBuildsAGreaterThanOrEqualsCriterion() {
    assertThat(CriterionBuilder.where(path(Sample::publishedYear)).gte(1965))
        .contains(Criteria.where("publishedYear").greaterThanOrEquals(1965));
  }

  @Test
  void lteBuildsALessThanOrEqualsCriterion() {
    assertThat(CriterionBuilder.where(path(Sample::publishedYear)).lte(1985))
        .contains(Criteria.where("publishedYear").lessThanOrEquals(1985));
  }

  @Test
  void betweenBuildsARangeCriterion() {
    assertThat(CriterionBuilder.where(path(Sample::publishedYear)).between(1965, 1985))
        .contains(Criteria.where("publishedYear").between(1965, 1985));
  }

  @Test
  void everyOperationIsAbsentForANullValue() {
    CriterionBuilder<String> title = CriterionBuilder.where(path(Sample::title));
    CriterionBuilder<Integer> year = CriterionBuilder.where(path(Sample::publishedYear));

    assertThat(title.is(null)).isEmpty();
    assertThat(title.containsIgnoreCase(null)).isEmpty();
    assertThat(title.in(null)).isEmpty();
    assertThat(year.gte(null)).isEmpty();
    assertThat(year.lte(null)).isEmpty();
    assertThat(year.between(null, 1985)).isEmpty();
    assertThat(year.between(1965, null)).isEmpty();
    assertThat(year.between(null, null)).isEmpty();
  }

  /** An empty set would render as {@code IN ()}, which is not valid SQL. */
  @Test
  void inIsAbsentForAnEmptyCollection() {
    assertThat(CriterionBuilder.where(path(Sample::title)).in(List.of())).isEmpty();
  }

  /** The typed path resolves to the property name, so renaming the accessor moves the column. */
  @Test
  void theTypedPathNamesTheColumnAfterTheProperty() {
    Criteria criteria = CriterionBuilder.where(path(Sample::publishedYear)).is(1965)
        .orElseThrow();

    assertThat(criteria.getColumn()).hasToString("publishedYear");
  }

}
