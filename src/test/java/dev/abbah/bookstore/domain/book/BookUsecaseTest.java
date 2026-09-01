package dev.abbah.bookstore.domain.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class BookUsecaseTest {

  private final InMemoryBookPort repository = new InMemoryBookPort();
  private final BookUsecase service = new BookUsecase(repository);

  private static long idOf(Book book) {
    return Objects.requireNonNull(book.id());
  }

  private static Book dune(String isbn) {
    return new Book(null, isbn, "Dune", "Frank Herbert", 1965, "Science Fiction");
  }

  @Test
  void createPersistsAndReturnsBookWithGeneratedId() {
    Book created = service.create(dune("978-0441013593"));

    assertThat(created.id()).isNotNull();
    assertThat(created.isbn()).isEqualTo("978-0441013593");
    assertThat(repository.findById(idOf(created))).contains(created);
  }

  @Test
  void createWithExistingIsbnThrowsDuplicateIsbn() {
    service.create(dune("978-0441013593"));
    Book duplicate = dune("978-0441013593");

    assertThatExceptionOfType(DuplicateIsbnException.class)
        .isThrownBy(() -> service.create(duplicate));
  }

  @Test
  void getReturnsExistingBook() {
    Book created = service.create(dune("978-0441013593"));

    assertThat(service.get(idOf(created))).isEqualTo(created);
  }

  @Test
  void getMissingIdThrowsBookNotFound() {
    assertThatExceptionOfType(BookNotFoundException.class).isThrownBy(() -> service.get(42L));
  }

  @Test
  void replaceMissingIdThrowsBookNotFound() {
    Book replacement = dune("978-0441013593");

    assertThatExceptionOfType(BookNotFoundException.class)
        .isThrownBy(() -> service.replace(42L, replacement));
  }

  @Test
  void replaceWithAnotherBooksIsbnThrowsDuplicateIsbn() {
    service.create(dune("isbn-a"));
    Book other = service.create(new Book(null, "isbn-b", "Hyperion", "Dan Simmons", 1989, null));
    long otherId = idOf(other);
    Book takenIsbn = dune("isbn-a");

    assertThatExceptionOfType(DuplicateIsbnException.class)
        .isThrownBy(() -> service.replace(otherId, takenIsbn));
  }

  @Test
  void replaceWithOwnIsbnIsAllowed() {
    Book created = service.create(dune("isbn-a"));

    Book replaced = service.replace(
        idOf(created),
        new Book(null, "isbn-a", "Dune Messiah", "Frank Herbert", 1969, "Science Fiction"));

    assertThat(replaced.id()).isEqualTo(idOf(created));
    assertThat(replaced.title()).isEqualTo("Dune Messiah");
  }

  @Test
  void deleteRemovesBook() {
    Book created = service.create(dune("isbn-a"));

    service.delete(idOf(created));

    assertThat(repository.existsById(idOf(created))).isFalse();
  }

  @Test
  void deleteMissingIdThrowsBookNotFound() {
    assertThatExceptionOfType(BookNotFoundException.class).isThrownBy(() -> service.delete(42L));
  }

  /** In-memory fake of the driven port — keeps the test free of mocking internals. */
  private static class InMemoryBookPort implements BookPort {

    private final Map<Long, Book> store = new HashMap<>();
    private long nextId = 1;

    @Override
    public Book save(Book book) {
      long id = book.id() != null ? book.id() : nextId++;
      Book saved = new Book(
          id, book.isbn(), book.title(), book.author(), book.publishedYear(), book.genre());
      store.put(id, saved);
      return saved;
    }

    @Override
    public Optional<Book> findById(long id) {
      return Optional.ofNullable(store.get(id));
    }

    @Override
    public Page<Book> findAll(BookFilter filter, Pageable pageable) {
      String title = filter.title();
      String author = filter.author();
      String isbn = filter.isbn();
      List<Book> books = store.values().stream()
          .filter(b -> title == null || b.title().toLowerCase().contains(title.toLowerCase()))
          .filter(b -> author == null || b.author().equals(author))
          .filter(b -> isbn == null || b.isbn().equals(isbn))
          .toList();
      List<Book> content = books.stream()
          .skip(pageable.getOffset())
          .limit(pageable.getPageSize())
          .toList();
      return new PageImpl<>(content, pageable, books.size());
    }

    @Override
    public boolean existsByIsbn(String isbn) {
      return store.values().stream().anyMatch(b -> b.isbn().equals(isbn));
    }

    @Override
    public boolean existsByIsbnAndIdNot(String isbn, long id) {
      return store.values().stream()
          .anyMatch(b -> b.isbn().equals(isbn) && !Long.valueOf(id).equals(b.id()));
    }

    @Override
    public boolean existsById(long id) {
      return store.containsKey(id);
    }

    @Override
    public void deleteById(long id) {
      store.remove(id);
    }

  }

}
