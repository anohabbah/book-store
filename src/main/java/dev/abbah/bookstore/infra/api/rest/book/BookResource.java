package dev.abbah.bookstore.infra.api.rest.book;

import dev.abbah.bookstore.domain.book.BookFilter;
import dev.abbah.bookstore.domain.book.BookUsecase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name="BookResource")
@RestController
@RequestMapping(
    path = "/v{version}/books",
    version = "1",
    produces = MediaType.APPLICATION_JSON_VALUE
)
class BookResource {

  /** Sorting is client-controlled, so only mapped book properties may reach Spring Data. */
  private static final Set<String> SORTABLE_PROPERTIES =
      Set.of("id", "isbn", "title", "author", "publishedYear", "genre");

  private final BookUsecase bookUsecase;
  private final BookDtoMapper bookDtoMapper;

  BookResource(BookUsecase bookUsecase, BookDtoMapper bookDtoMapper) {
    this.bookUsecase = bookUsecase;
    this.bookDtoMapper = bookDtoMapper;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(operationId = "createBook", summary = "Create a new book", description = "Create a new book")
  ResponseEntity<BookDto> create(@Valid @RequestBody CreateBookRequest request) {
    BookDto created = bookDtoMapper.toDto(
        bookUsecase.create(bookDtoMapper.toDomain(request)));
    return ResponseEntity.created(URI.create("/v1/books/" + created.id())).body(created);
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getBookById", summary = "Get a book by id", description = "Get a book by id")
  BookDto get(@PathVariable long id) {
    return bookDtoMapper.toDto(bookUsecase.get(id));
  }

  @GetMapping
  @Operation(operationId = "listBooks", summary = "List books", description = "List books")
  PagedModel<BookDto> list(
      @RequestParam(required = false) @Nullable String title,
      @RequestParam(required = false) @Nullable String author,
      @RequestParam(required = false) @Nullable String isbn,
      @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
    rejectUnknownSortProperties(pageable.getSort());
    // BookFilter is constructed here rather than bound with @ParameterObject: binding it would
    // put a domain record on the web edge and change the documented query parameters.
    return new PagedModel<>(
        bookUsecase.list(new BookFilter(title, author, isbn), pageable).map(bookDtoMapper::toDto));
  }

  // An unmapped sort property would blow up inside Spring Data as a 500; report it as the
  // client error it is.
  private static void rejectUnknownSortProperties(Sort sort) {
    String unknown = sort.stream()
        .map(Sort.Order::getProperty)
        .filter(property -> !SORTABLE_PROPERTIES.contains(property))
        .collect(Collectors.joining(", "));
    if (!unknown.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Books cannot be sorted by: " + unknown);
    }
  }

  @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(operationId = "replaceBookById", summary = "Replace a book by id", description = "Replace a book by id")
  BookDto replace(@PathVariable long id, @Valid @RequestBody CreateBookRequest request) {
    return bookDtoMapper.toDto(bookUsecase.replace(id, bookDtoMapper.toDomain(request)));
  }

  @DeleteMapping("/{id}")
  @Operation(operationId = "deleteBookById", summary = "Delete a book by id", description = "Delete a book by id")
  ResponseEntity<Void> delete(@PathVariable long id) {
    bookUsecase.delete(id);
    return ResponseEntity.noContent().build();
  }

}
