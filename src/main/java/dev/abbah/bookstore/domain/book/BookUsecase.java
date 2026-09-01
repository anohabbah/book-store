package dev.abbah.bookstore.domain.book;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Class, not record: behavioural @Service holding the book use-case rules (design D5).
@Service
@Transactional(readOnly = true)
public class BookUsecase {

  private final BookPort bookPort;

  public BookUsecase(BookPort bookPort) {
    this.bookPort = bookPort;
  }

  @Transactional
  public Book create(Book book) {
    if (bookPort.existsByIsbn(book.isbn())) {
      throw new DuplicateIsbnException(book.isbn());
    }
    return bookPort.save(book);
  }

  public Book get(long id) {
    return bookPort.findById(id).orElseThrow(() -> new BookNotFoundException(id));
  }


  public Page<Book> list(BookFilter filter, Pageable pageable) {
    return bookPort.findAll(filter, pageable);
  }

  @Transactional
  public Book replace(long id, Book book) {
    if (!bookPort.existsById(id)) {
      throw new BookNotFoundException(id);
    }
    if (bookPort.existsByIsbnAndIdNot(book.isbn(), id)) {
      throw new DuplicateIsbnException(book.isbn());
    }
    return bookPort.save(
        new Book(id, book.isbn(), book.title(), book.author(), book.publishedYear(), book.genre()));
  }

  @Transactional
  public void delete(long id) {
    if (!bookPort.existsById(id)) {
      throw new BookNotFoundException(id);
    }
    bookPort.deleteById(id);
  }

}
