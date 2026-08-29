package dev.abbah.bookstore.domain.book;

// Class, not record: exceptions must extend RuntimeException (design D5).
public class BookNotFoundException extends RuntimeException {

  public BookNotFoundException(long id) {
    super("Book %d not found".formatted(id));
  }

}
