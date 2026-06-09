## 1. Schema & package scaffolding

- [ ] 1.1 Write `V1__create_books_table.sql` under `src/main/resources/db/migration/`
  creating
  the `books` table per design D7 (surrogate `id bigint generated always as identity`
  PK; `isbn varchar(20) not null` with `constraint uq_books_isbn unique`;
  `title varchar(512) not null`; `author varchar(256) not null`;
  `published_year integer`; `genre varchar(128)`). Apply postgres-patterns to the schema.
- [ ] 1.2 Add `@NullMarked` `package-info.java` to the three new packages
  (`domain/catalog`, `infra/spi/db/catalog`, `infra/api/rest/catalog`) using
  `org.jspecify.annotations.NullMarked`.
- [ ] 1.3 (RED) Add a migration smoke test (e.g. `CatalogSchemaIT`) that boots
  `@SpringBootTest` with `@Import(TestcontainersConfiguration.class)` and asserts Flyway
  applied `V1` and the `books` table exists; (GREEN) confirm it passes against the shared
  Testcontainers Postgres. Pin image tags, no `:latest`.

## 2. Domain: Book, port, and CatalogService (TDD)

- [ ] 2.1 (RED) Write `CatalogServiceTest` (unit/slice, port stubbed/faked) asserting the
  use-case rules from the spec: create persists and returns a book; create with an
  existing ISBN throws `DuplicateIsbnException`; get/replace/delete of a missing id throws
  `BookNotFoundException`; replace whose ISBN belongs to a *different* book throws
  `DuplicateIsbnException`; replace with the book's own ISBN is allowed.
- [ ] 2.2 Define `Book` as a record per design D3 — `@Nullable Long id`, non-null
  `isbn/title/author`, `@Nullable Integer publishedYear`, `@Nullable String genre`. Apply
  jspecify-spring-framework-patterns for the `@Nullable` id decision (legitimately absent
  before persistence → `@Nullable`, not `NullAway.Init`); cite only that skill.
- [ ] 2.3 Define the `BookRepository` driven port (interface) in `domain/catalog` with the
  operations the service needs (`save`, `findById`, paginated/filtered `findAll`,
  `existsByIsbn` excluding-self, `deleteById`/`existsById`). No Spring Data types in the
  signature — keep the domain infra-free.
- [ ] 2.4 Add `BookNotFoundException` and `DuplicateIsbnException` (classes extending
  `RuntimeException` — documented record exception per design D5).
- [ ] 2.5 (GREEN) Implement `CatalogService` (`@Service`, constructor injection,
  `@Transactional` with `readOnly = true` on queries) to satisfy 2.1; apply
  springboot-patterns for the service layer. (REFACTOR) Tidy while keeping the test green.

## 3. Persistence adapter (TDD)

- [ ] 3.1 (RED) Write a Spring Data JDBC slice/integration test (e.g. `BookDbAdapterIT`
  with `@Import(TestcontainersConfiguration.class)`) covering round-trip save/find,
  `existsByIsbn`, the `UNIQUE(isbn)` backstop surfacing as `DuplicateIsbnException`, and
  the `title` (case-insensitive `ILIKE`), `author`, `isbn` filters combining
  conjunctively with pagination.
- [ ] 3.2 Define `BookEntity` as a Spring Data JDBC aggregate record (`@Table("books")`,
  `@Id @Nullable Long id`, snake_case column mapping) per design D3/D7; apply
  jspecify-spring-framework-patterns for the `@Nullable` id (cite only that skill).
- [ ] 3.3 Define `BookCrudRepository` (extends `PagingAndSortingRepository` +
  `CrudRepository`, or `ListCrudRepository`) with the derived finders / `@Query` for the
  filter combinations (`ILIKE` for title, equality for author/isbn) per design D6; apply
  postgres-patterns to the query.
- [ ] 3.4 Define `BookMapper` (MapStruct, Spring component model) mapping `BookEntity ↔
  Book`.
- [ ] 3.5 (GREEN) Implement `BookDbAdapter` (`@Component implements BookRepository`),
  delegating to `BookCrudRepository`/`BookMapper` and translating
  `DataIntegrityViolationException`/`DbActionExecutionException` on the ISBN constraint
  into `DuplicateIsbnException` (design D4). (REFACTOR) keep 3.1 green.

## 4. REST API (TDD)

- [ ] 4.1 (RED) Write `BookResourceIT` (`@SpringBootTest`, `@AutoConfigureMockMvc` /
  `MockMvcTester`, `@Import(TestcontainersConfiguration.class)`) covering every spec
  scenario: `POST` 201 + `Location`; `POST` invalid → 400; `POST` duplicate ISBN → 409;
  `GET /{id}` 200 / 404; `GET` list pagination + title/author/isbn filters; `PUT` 200 /
  400 / 404 / 409-on-collision; `DELETE` 204 / 404; and that responses carry no
  stock/availability fields.
- [ ] 4.2 Define request/response DTO records `BookRequest` (`@NotBlank isbn/title/author`,
  size limits, nullable `publishedYear`/`genre`) and `BookResponse` (non-null `id`) per
  design D3; apply springboot-patterns/validation for the constraints.
- [ ] 4.3 Define `BookRestMapper` (MapStruct, Spring component model) mapping
  `BookRequest → Book` and `Book → BookResponse`.
- [ ] 4.4 (GREEN) Implement `BookResource` (`@RestController` at `/books`, thin —
  delegates to `CatalogService`, returns `ResponseEntity` with status/`Location`) for all
  five verbs and the paginated/filtered list.
- [ ] 4.5 Implement `CatalogExceptionHandler` (`@RestControllerAdvice`) translating
  `BookNotFoundException`→404, `DuplicateIsbnException`→409,
  `MethodArgumentNotValidException`→400 as RFC 7807 `ProblemDetail` (design D6).
  (REFACTOR) keep 4.1 green.

## 5. Verification

- [ ] 5.1 Run `./gradlew build` and ensure it passes with **zero NullAway errors**. For
  any nullness violation, fix it by correct JSpecify annotation or real null handling
  (guided by jspecify-spring-framework-patterns / jspecify-user-guide as applicable, cite
  only those used); use `@SuppressWarnings` only with a documented justification, never to
  silence the error count. Keep every fix inside the `catalog` packages; if a dependency's
  nullness is wrong, note it explicitly rather than silently changing it.
- [ ] 5.2 Confirm all new tests pass (`CatalogServiceTest`, `BookDbAdapterIT`,
  `BookResourceIT`, schema test) against the shared Testcontainers Postgres, and that the
  full integration suite is green.
- [ ] 5.3 Sanity-check the API via Swagger UI (springdoc) and confirm the `/books`
  contract and `ProblemDetail` error bodies match the spec.
