package dev.abbah.bookstore.infra.spi.db.book;

import org.springframework.data.repository.ListCrudRepository;

public interface BookCrudRepository extends ListCrudRepository<BookEntity, Long> {

  boolean existsByIsbn(String isbn);

  boolean existsByIsbnAndIdNot(String isbn, long id);

}
