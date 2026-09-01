package dev.abbah.bookstore.infra.spi.db;

import java.util.Optional;
import org.springframework.data.relational.core.query.Criteria;

/**
 * One named, reusable filtering rule: it reads a filter and either contributes a
 * {@link Criteria} or does not apply.
 *
 * <p>{@link Optional#empty()} is the single representation of "does not apply" — never
 * {@link Criteria#empty()}, which is reserved for the identity of the fold that combines
 * criteria.
 *
 * <p>Implementations get the whole filter, so a rule may read more than one of its fields. A rule
 * that does must say so in a comment naming the other field, or it becomes discoverable only by
 * grep.
 *
 * @param <F> the filter type this criterion reads
 */
@FunctionalInterface
public interface Criterion<F> {

  Optional<Criteria> toCriteria(F filter);

}
