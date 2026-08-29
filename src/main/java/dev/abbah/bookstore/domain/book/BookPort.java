package dev.abbah.bookstore.domain.book;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Driven port for book persistence, owned by the domain. */
public interface BookPort {

  Book save(Book book);

  Optional<Book> findById(long id);

  Page<Book> findAll(
      @Nullable String title, @Nullable String author, @Nullable String isbn, Pageable pageable);

  boolean existsByIsbn(String isbn);

  boolean existsByIsbnAndIdNot(String isbn, long id);

  boolean existsById(long id);

  void deleteById(long id);

}
