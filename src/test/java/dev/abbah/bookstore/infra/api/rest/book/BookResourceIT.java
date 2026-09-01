package dev.abbah.bookstore.infra.api.rest.book;

import static org.assertj.core.api.Assertions.assertThat;

import dev.abbah.bookstore.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookResourceIT {

  @Autowired
  private MockMvcTester mvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanBooks() {
    jdbcTemplate.update("delete from books");
  }

  private static String bookJson(String isbn, String title, String author) {
    return """
        {"isbn": "%s", "title": "%s", "author": "%s", "publishedYear": 1965, "genre": "Science Fiction"}
        """.formatted(isbn, title, author);
  }

  private MvcTestResult post(String json) {
    return mvc.post().uri("/v1/books")
        .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
  }

  private MvcTestResult put(long id, String json) {
    return mvc.put().uri("/v1/books/{id}", id)
        .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
  }

  private long createBook(String isbn, String title, String author) {
    assertThat(post(bookJson(isbn, title, author))).hasStatus(HttpStatus.CREATED);
    Long id = jdbcTemplate.queryForObject("select id from books where isbn = ?", Long.class, isbn);
    return java.util.Objects.requireNonNull(id);
  }

  private int bookCount() {
    Integer count = jdbcTemplate.queryForObject("select count(*) from books", Integer.class);
    return count != null ? count : 0;
  }

  // --- POST /books ---

  @Test
  void postCreatesBookWithLocationAndGeneratedId() {
    MvcTestResult result = post(bookJson("978-0441013593", "Dune", "Frank Herbert"));

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(result).bodyJson().extractingPath("$.id").isNotNull();
    assertThat(result).bodyJson().extractingPath("$.isbn").isEqualTo("978-0441013593");
    assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Dune");
    Long id = jdbcTemplate.queryForObject(
        "select id from books where isbn = '978-0441013593'", Long.class);
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/v1/books/" + id);
  }

  @Test
  void postWithBlankRequiredFieldsReturns400AndPersistsNothing() {
    MvcTestResult result = post("""
        {"isbn": "", "title": "Dune", "author": ""}
        """);

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(bookCount()).isZero();
  }

  @Test
  void postWithDuplicateIsbnReturns409AndCreatesNoSecondRecord() {
    post(bookJson("isbn-a", "Dune", "Frank Herbert"));

    MvcTestResult result = post(bookJson("isbn-a", "Dune (again)", "Frank Herbert"));

    assertThat(result).hasStatus(HttpStatus.CONFLICT);
    assertThat(bookCount()).isEqualTo(1);
  }

  // --- GET /books/{id} ---

  @Test
  void getReturnsExistingBook() {
    long id = createBook("isbn-a", "Dune", "Frank Herbert");

    MvcTestResult result = mvc.get().uri("/v1/books/{id}", id).exchange();

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.isbn").isEqualTo("isbn-a");
    assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Dune");
    assertThat(result).bodyJson().extractingPath("$.author").isEqualTo("Frank Herbert");
  }

  @Test
  void getMissingBookReturns404() {
    assertThat(mvc.get().uri("/v1/books/{id}", 4242).exchange()).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void bookRepresentationCarriesOnlyBibliographicFields() {
    long id = createBook("isbn-a", "Dune", "Frank Herbert");

    assertThat(mvc.get().uri("/v1/books/{id}", id).exchange())
        .bodyJson().extractingPath("$").asMap()
        .containsOnlyKeys("id", "isbn", "title", "author", "publishedYear", "genre");
  }

  // --- GET /books (list) ---

  @Test
  void listPaginatesWithMetadata() {
    createBook("isbn-a", "Dune", "Frank Herbert");
    createBook("isbn-b", "Dune Messiah", "Frank Herbert");
    createBook("isbn-c", "Children of Dune", "Frank Herbert");

    MvcTestResult result = mvc.get().uri("/v1/books?page=1&size=2").exchange();

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
    assertThat(result).bodyJson().extractingPath("$.page.number").isEqualTo(1);
    assertThat(result).bodyJson().extractingPath("$.page.size").isEqualTo(2);
    assertThat(result).bodyJson().extractingPath("$.page.totalElements").isEqualTo(3);
    assertThat(result).bodyJson().extractingPath("$.page.totalPages").isEqualTo(2);
  }

  @Test
  void listSortsByTheRequestedProperty() {
    createBook("isbn-a", "Dune", "Frank Herbert");
    createBook("isbn-b", "Children of Dune", "Frank Herbert");

    MvcTestResult result = mvc.get().uri("/v1/books?sort=title,desc").exchange();

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.content[0].title").isEqualTo("Dune");
    assertThat(result).bodyJson().extractingPath("$.content[1].title")
        .isEqualTo("Children of Dune");
  }

  @Test
  void listRejectsAnUnknownSortProperty() {
    assertThat(mvc.get().uri("/v1/books?sort=bogus,asc").exchange())
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void listFiltersByTitleSubstringCaseInsensitively() {
    createBook("isbn-a", "Dune", "Frank Herbert");
    createBook("isbn-b", "Hyperion", "Dan Simmons");

    MvcTestResult result = mvc.get().uri("/v1/books?title=dune").exchange();

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
    assertThat(result).bodyJson().extractingPath("$.content[0].title").isEqualTo("Dune");
  }

  /** A blank filter parameter reads as "not filtering", not as "match the empty string". */
  @Test
  void listTreatsABlankFilterParameterAsAbsent() {
    createBook("isbn-a", "Dune", "Frank Herbert");
    createBook("isbn-b", "Hyperion", "Dan Simmons");

    MvcTestResult result = mvc.get().uri("/v1/books?title=").exchange();

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.page.totalElements").isEqualTo(2);
  }

  @Test
  void listFiltersByExactIsbn() {
    createBook("isbn-a", "Dune", "Frank Herbert");
    createBook("isbn-b", "Hyperion", "Dan Simmons");

    assertThat(mvc.get().uri("/v1/books?isbn=isbn-b").exchange())
        .bodyJson().extractingPath("$.content[0].isbn").isEqualTo("isbn-b");
    assertThat(mvc.get().uri("/v1/books?isbn=missing").exchange())
        .bodyJson().extractingPath("$.page.totalElements").isEqualTo(0);
  }

  // --- PUT /books/{id} ---

  @Test
  void putReplacesExistingBook() {
    long id = createBook("isbn-a", "Dune", "Frank Herbert");

    MvcTestResult result = put(id, bookJson("isbn-a", "Dune Messiah", "Frank Herbert"));

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.id").isEqualTo((int) id);
    assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Dune Messiah");
  }

  @Test
  void putMissingBookReturns404() {
    assertThat(put(4242, bookJson("isbn-a", "Dune", "Frank Herbert")))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void putWithAnotherBooksIsbnReturns409AndLeavesBothUnchanged() {
    createBook("isbn-a", "Dune", "Frank Herbert");
    long otherId = createBook("isbn-b", "Hyperion", "Dan Simmons");

    MvcTestResult result = put(otherId, bookJson("isbn-a", "Hyperion Reborn", "Dan Simmons"));

    assertThat(result).hasStatus(HttpStatus.CONFLICT);
    assertThat(jdbcTemplate.queryForList("select title from books order by id", String.class))
        .containsExactly("Dune", "Hyperion");
  }

  @Test
  void putWithInvalidFieldsReturns400AndLeavesBookUnchanged() {
    long id = createBook("isbn-a", "Dune", "Frank Herbert");

    MvcTestResult result = put(id, """
        {"isbn": "isbn-a", "title": "", "author": "Frank Herbert"}
        """);

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(jdbcTemplate.queryForObject("select title from books where id = ?", String.class, id))
        .isEqualTo("Dune");
  }

  // --- DELETE /books/{id} ---

  @Test
  void deleteRemovesBookAndReturns204() {
    long id = createBook("isbn-a", "Dune", "Frank Herbert");

    assertThat(mvc.delete().uri("/v1/books/{id}", id).exchange()).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(mvc.get().uri("/v1/books/{id}", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void deleteMissingBookReturns404() {
    assertThat(mvc.delete().uri("/v1/books/{id}", 4242).exchange()).hasStatus(HttpStatus.NOT_FOUND);
  }

}
