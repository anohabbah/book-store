package dev.abbah.bookstore.infra.api.rest.book;

import dev.abbah.bookstore.domain.book.BookNotFoundException;
import dev.abbah.bookstore.domain.book.DuplicateIsbnException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates book domain exceptions into RFC 7807 problem details.
 * {@code MethodArgumentNotValidException} → 400 is already a ProblemDetail via
 * {@code spring.mvc.problemdetails.enabled}.
 */
@RestControllerAdvice
class BookExceptionHandler {

  @ExceptionHandler(BookNotFoundException.class)
  ProblemDetail handleBookNotFound(BookNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(DuplicateIsbnException.class)
  ProblemDetail handleDuplicateIsbn(DuplicateIsbnException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

}
