package dev.abbah.bookstore.domain.book;

// Class, not record: exceptions must extend RuntimeException (design D5).
public class DuplicateIsbnException extends RuntimeException {

  public DuplicateIsbnException(String isbn) {
    super("A book with ISBN %s already exists".formatted(isbn));
  }

}
