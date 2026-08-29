package dev.abbah.bookstore.infra.spi.db.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.abbah.bookstore.TestcontainersConfiguration;
import dev.abbah.bookstore.domain.book.Book;
import dev.abbah.bookstore.domain.book.BookPort;
import dev.abbah.bookstore.domain.book.DuplicateIsbnException;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookAdapterIT {

  @Autowired
  private BookPort bookPort;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanBooks() {
    jdbcTemplate.update("delete from books");
  }

  private static long idOf(Book book) {
    return Objects.requireNonNull(book.id());
  }

  private Book save(String isbn, String title, String author) {
    return bookPort.save(new Book(null, isbn, title, author, null, null));
  }

  private static Pageable byId(int page, int size) {
    return PageRequest.of(page, size, Sort.by("id"));
  }

  @Test
  void savedBookRoundTripsThroughFindById() {
    Book saved = bookPort.save(
        new Book(null, "978-0441013593", "Dune", "Frank Herbert", 1965, "Science Fiction"));

    assertThat(saved.id()).isNotNull();
    assertThat(bookPort.findById(idOf(saved))).contains(saved);
  }

  @Test
  void existsByIsbnReflectsStoredBooks() {
    save("isbn-a", "Dune", "Frank Herbert");

    assertThat(bookPort.existsByIsbn("isbn-a")).isTrue();
    assertThat(bookPort.existsByIsbn("isbn-b")).isFalse();
  }

  @Test
  void existsByIsbnAndIdNotExcludesSelf() {
    Book dune = save("isbn-a", "Dune", "Frank Herbert");
    save("isbn-b", "Hyperion", "Dan Simmons");

    assertThat(bookPort.existsByIsbnAndIdNot("isbn-a", idOf(dune))).isFalse();
    assertThat(bookPort.existsByIsbnAndIdNot("isbn-b", idOf(dune))).isTrue();
  }

  @Test
  void uniqueIsbnConstraintSurfacesAsDuplicateIsbnException() {
    save("isbn-a", "Dune", "Frank Herbert");

    assertThatExceptionOfType(DuplicateIsbnException.class)
        .isThrownBy(() -> save("isbn-a", "Dune (again)", "Frank Herbert"));
  }

  @Test
  void titleFilterMatchesCaseInsensitiveSubstring() {
    save("isbn-a", "Dune", "Frank Herbert");
    save("isbn-b", "Dune Messiah", "Frank Herbert");
    save("isbn-c", "Hyperion", "Dan Simmons");

    Page<Book> page = bookPort.findAll("dune", null, null, byId(0, 10));

    assertThat(page.getContent()).extracting(Book::title).containsExactly("Dune", "Dune Messiah");
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  void authorAndIsbnFiltersUseEquality() {
    save("isbn-a", "Dune", "Frank Herbert");
    save("isbn-b", "Hyperion", "Dan Simmons");

    assertThat(bookPort.findAll(null, "Dan Simmons", null, byId(0, 10)).getContent())
        .extracting(Book::isbn).containsExactly("isbn-b");
    assertThat(bookPort.findAll(null, null, "isbn-a", byId(0, 10)).getContent())
        .extracting(Book::isbn).containsExactly("isbn-a");
    assertThat(bookPort.findAll(null, "Frank", null, byId(0, 10)).getContent()).isEmpty();
  }

  @Test
  void filtersCombineConjunctively() {
    save("isbn-a", "Dune", "Frank Herbert");
    save("isbn-b", "Dune Messiah", "Frank Herbert");
    save("isbn-c", "Dune: The Graphic Novel", "Brian Herbert");

    Page<Book> page = bookPort.findAll("dune", "Frank Herbert", null, byId(0, 10));

    assertThat(page.getContent()).extracting(Book::isbn).containsExactly("isbn-a", "isbn-b");
  }

  @Test
  void findAllPaginates() {
    save("isbn-a", "Dune", "Frank Herbert");
    save("isbn-b", "Dune Messiah", "Frank Herbert");
    save("isbn-c", "Children of Dune", "Frank Herbert");

    Page<Book> secondPage = bookPort.findAll(null, null, null, byId(1, 2));

    assertThat(secondPage.getContent()).hasSize(1);
    assertThat(secondPage.getNumber()).isEqualTo(1);
    assertThat(secondPage.getSize()).isEqualTo(2);
    assertThat(secondPage.getTotalElements()).isEqualTo(3);
    assertThat(secondPage.getTotalPages()).isEqualTo(2);
  }

  @Test
  void findAllHonoursTheRequestedSort() {
    save("isbn-a", "Dune", "Frank Herbert");
    save("isbn-b", "Children of Dune", "Frank Herbert");
    save("isbn-c", "Dune Messiah", "Frank Herbert");

    Page<Book> page = bookPort.findAll(
        null, null, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "title")));

    assertThat(page.getContent()).extracting(Book::title)
        .containsExactly("Dune Messiah", "Dune", "Children of Dune");
  }

  @Test
  void deleteByIdRemovesBook() {
    Book saved = save("isbn-a", "Dune", "Frank Herbert");

    bookPort.deleteById(idOf(saved));

    assertThat(bookPort.existsById(idOf(saved))).isFalse();
    assertThat(bookPort.findById(idOf(saved))).isEmpty();
  }

}
