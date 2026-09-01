package dev.abbah.bookstore.infra.spi.db.book;

import dev.abbah.bookstore.domain.book.Book;
import dev.abbah.bookstore.domain.book.BookFilter;
import dev.abbah.bookstore.domain.book.BookPort;
import dev.abbah.bookstore.domain.book.DuplicateIsbnException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

@Component
class BookAdapter implements BookPort {

  private static final String ISBN_CONSTRAINT = "uq_books_isbn";

  private final BookCrudRepository bookCrudRepository;
  private final JdbcAggregateOperations jdbcAggregateOperations;
  private final BookEntityMapper bookEntityMapper;

  BookAdapter(
      BookCrudRepository bookCrudRepository,
      JdbcAggregateOperations jdbcAggregateOperations,
      BookEntityMapper bookEntityMapper) {
    this.bookCrudRepository = bookCrudRepository;
    this.jdbcAggregateOperations = jdbcAggregateOperations;
    this.bookEntityMapper = bookEntityMapper;
  }

  @Override
  public Book save(Book book) {
    try {
      return bookEntityMapper.toDomain(bookCrudRepository.save(bookEntityMapper.toEntity(book)));
    } catch (DataIntegrityViolationException | DbActionExecutionException e) {
      if (mentionsIsbnConstraint(e)) {
        throw new DuplicateIsbnException(book.isbn());
      }
      throw e;
    }
  }

  // Race-condition backstop (design D4): the UNIQUE(isbn) violation must surface as the
  // same domain exception as the service pre-check, whatever Spring wraps it in.
  private static boolean mentionsIsbnConstraint(Throwable e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      String message = cause.getMessage();
      if (message != null && message.contains(ISBN_CONSTRAINT)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Optional<Book> findById(long id) {
    return bookCrudRepository.findById(id).map(bookEntityMapper::toDomain);
  }

  // Criteria rather than a hand-written @Query: Spring Data JDBC's string-based @Query cannot
  // take a Pageable, so limit/offset/sort would all have to be spelled out in SQL. Query.with
  // applies them instead, and PageableExecutionUtils skips the count query when the page
  // already tells us the total.
  @Override
  public Page<Book> findAll(BookFilter filter, Pageable pageable) {
    // Criteria.empty() is the identity of the fold and appears only here; a criterion that does
    // not apply says so with Optional.empty().
    Criteria criteria = Stream.of(BookCriterion.values())
        .map(criterion -> criterion.toCriteria(filter))
        .flatMap(Optional::stream)
        .reduce(Criteria::and)
        .orElseGet(Criteria::empty);
    Query query = Query.query(criteria);
    List<Book> content = jdbcAggregateOperations.findAll(query.with(pageable), BookEntity.class)
        .stream()
        .map(bookEntityMapper::toDomain)
        .toList();
    return PageableExecutionUtils.getPage(
        content, pageable, () -> jdbcAggregateOperations.count(query, BookEntity.class));
  }

  @Override
  public boolean existsByIsbn(String isbn) {
    return bookCrudRepository.existsByIsbn(isbn);
  }

  @Override
  public boolean existsByIsbnAndIdNot(String isbn, long id) {
    return bookCrudRepository.existsByIsbnAndIdNot(isbn, id);
  }

  @Override
  public boolean existsById(long id) {
    return bookCrudRepository.existsById(id);
  }

  @Override
  public void deleteById(long id) {
    bookCrudRepository.deleteById(id);
  }

}
