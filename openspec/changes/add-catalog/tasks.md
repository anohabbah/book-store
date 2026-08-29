## 1. Schema & package scaffolding

- [x] 1.1 Write `V1__create_books_table.sql` under `src/main/resources/db/migration/`
  creating
  the `books` table per design D7 (surrogate `id bigint generated always as identity`
  PK; `isbn varchar(20) not null` with `constraint uq_books_isbn unique`;
  `title varchar(512) not null`; `author varchar(256) not null`;
  `published_year integer`; `genre varchar(128)`). Apply postgres-patterns to the schema.
- [x] 1.2 Add `@NullMarked` `package-info.java` to the three new packages
  (`domain/book`, `infra/spi/db/book`, `infra/api/rest/book`) using
  `org.jspecify.annotations.NullMarked`.
- [x] 1.3 (RED) Add a migration smoke test (e.g. `BookSchemaIT`) that boots
  `@SpringBootTest` with `@Import(TestcontainersConfiguration.class)` and asserts Flyway
  applied `V1` and the `books` table exists; (GREEN) confirm it passes against the shared
  Testcontainers Postgres. Pin image tags, no `:latest`.

## 2. Domain: Book, port, and BookUsecase (TDD)

- [x] 2.1 (RED) Write `BookUsecaseTest` (unit/slice, port stubbed/faked) asserting the
  use-case rules from the spec: create persists and returns a book; create with an
  existing ISBN throws `DuplicateIsbnException`; get/replace/delete of a missing id throws
  `BookNotFoundException`; replace whose ISBN belongs to a *different* book throws
  `DuplicateIsbnException`; replace with the book's own ISBN is allowed.
- [x] 2.2 Define `Book` as a record per design D3 — `@Nullable Long id`, non-null
  `isbn/title/author`, `@Nullable Integer publishedYear`, `@Nullable String genre`. Apply
  jspecify-spring-framework-patterns for the `@Nullable` id decision (legitimately absent
  before persistence → `@Nullable`, not `NullAway.Init`); cite only that skill.
- [x] 2.3 Define the `BookPort` driven port (interface) in `domain/book` with the
  operations the service needs (`save`, `findById`, paginated/filtered `findAll`,
  `existsByIsbn` excluding-self, `deleteById`/`existsById`). *Superseded by 6.2: the port
  speaks Spring Data's `Page`/`Pageable` (design D8); everything else stays infra-free.*
- [x] 2.4 Add `BookNotFoundException` and `DuplicateIsbnException` (classes extending
  `RuntimeException` — documented record exception per design D5).
- [x] 2.5 (GREEN) Implement `BookUsecase` (`@Service`, constructor injection,
  `@Transactional` with `readOnly = true` on queries) to satisfy 2.1; apply
  springboot-patterns for the service layer. (REFACTOR) Tidy while keeping the test green.

## 3. Persistence adapter (TDD)

- [x] 3.1 (RED) Write a Spring Data JDBC slice/integration test (e.g. `BookAdapterIT`
  with `@Import(TestcontainersConfiguration.class)`) covering round-trip save/find,
  `existsByIsbn`, the `UNIQUE(isbn)` backstop surfacing as `DuplicateIsbnException`, and
  the `title` (case-insensitive `ILIKE`), `author`, `isbn` filters combining
  conjunctively with pagination.
- [x] 3.2 Define `BookEntity` as a Spring Data JDBC aggregate record (`@Table("books")`,
  `@Id @Nullable Long id`, snake_case column mapping) per design D3/D7; apply
  jspecify-spring-framework-patterns for the `@Nullable` id (cite only that skill).
- [x] 3.3 Define `BookCrudRepository` (extends `PagingAndSortingRepository` +
  `CrudRepository`, or `ListCrudRepository`) with the derived finders / `@Query` for the
  filter combinations (`ILIKE` for title, equality for author/isbn) per design D6; apply
  postgres-patterns to the query. *Superseded by 6.3: the filtered listing moved to a
  `Criteria` query on `JdbcAggregateOperations`, because a string `@Query` cannot take a
  `Pageable`.*
- [x] 3.4 Define `BookEntityMapper` (MapStruct, Spring component model) mapping
  `BookEntity ↔ Book`.
- [x] 3.5 (GREEN) Implement `BookAdapter` (`@Component implements BookPort`),
  delegating to `BookCrudRepository`/`BookEntityMapper` and translating
  `DataIntegrityViolationException`/`DbActionExecutionException` on the ISBN constraint
  into `DuplicateIsbnException` (design D4). (REFACTOR) keep 3.1 green.

## 4. REST API (TDD)

- [x] 4.1 (RED) Write `BookResourceIT` (`@SpringBootTest`, `@AutoConfigureMockMvc` /
  `MockMvcTester`, `@Import(TestcontainersConfiguration.class)`) covering every spec
  scenario: `POST` 201 + `Location`; `POST` invalid → 400; `POST` duplicate ISBN → 409;
  `GET /{id}` 200 / 404; `GET` list pagination + title/author/isbn filters; `PUT` 200 /
  400 / 404 / 409-on-collision; `DELETE` 204 / 404; and that responses carry no
  stock/availability fields.
- [x] 4.2 Define request/response DTO records `CreateBookRequest` (`@NotBlank
  isbn/title/author`, size limits, nullable `publishedYear`/`genre`) and `BookDto`
  (non-null `id`) per design D3; apply springboot-patterns/validation for the constraints.
- [x] 4.3 Define `BookDtoMapper` (MapStruct, Spring component model) mapping
  `CreateBookRequest → Book` and `Book → BookDto`.
- [x] 4.4 (GREEN) Implement `BookResource` (`@RestController` at `/v1/books`, thin —
  delegates to `BookUsecase`, returns `ResponseEntity` with status/`Location`) for all
  five verbs and the paginated/filtered list.
- [x] 4.5 Implement `BookExceptionHandler` (`@RestControllerAdvice`) translating
  `BookNotFoundException`→404, `DuplicateIsbnException`→409,
  `MethodArgumentNotValidException`→400 as RFC 7807 `ProblemDetail` (design D6).
  (REFACTOR) keep 4.1 green.

## 5. Verification

- [x] 5.1 Run `./gradlew build` and ensure it passes with **zero NullAway errors**. For
  any nullness violation, fix it by correct JSpecify annotation or real null handling
  (guided by jspecify-spring-framework-patterns / jspecify-user-guide as applicable, cite
  only those used); use `@SuppressWarnings` only with a documented justification, never to
  silence the error count. Keep every fix inside the `book` packages; if a dependency's
  nullness is wrong, note it explicitly rather than silently changing it.
- [x] 5.2 Confirm all new tests pass (`BookUsecaseTest`, `BookAdapterIT`,
  `BookResourceIT`, schema test) against the shared Testcontainers Postgres, and that the
  full integration suite is green.
- [x] 5.3 Sanity-check the API via Swagger UI (springdoc) and confirm the `/v1/books`
  contract and `ProblemDetail` error bodies match the spec.

## 6. Refactor: adopt Spring Data pagination (TDD)

- [x] 6.1 (RED) Retarget the paging tests before touching production code, per the tdd
  skill: `BookAdapterIT` calls `findAll(..., PageRequest.of(p, s, Sort.by("id")))` and
  asserts on `Page<Book>` (`getContent`/`getNumber`/`getSize`/`getTotalElements`), plus a
  new `findAllHonoursTheRequestedSort` case; `BookResourceIT` asserts the `PagedModel`
  envelope (`$.content`, `$.page.number`, `$.page.size`, `$.page.totalElements`,
  `$.page.totalPages`) and gains `listSortsByTheRequestedProperty` and
  `listRejectsAnUnknownSortProperty` (→ `400`); `BookUsecaseTest`'s in-memory port fake
  returns `PageImpl` paged off `Pageable#getOffset`/`getPageSize`.
- [x] 6.2 (GREEN) Change the port and use case to `Page<Book> findAll(title, author, isbn,
  Pageable)` / `BookUsecase#list(..., Pageable)`, then **delete
  `domain/book/BookPage.java`**.
- [x] 6.3 (GREEN) Rewrite `BookAdapter#findAll` on a programmatic `Criteria` executed via
  `JdbcAggregateOperations` — `Query.with(pageable)` for limit/offset/sort and
  `PageableExecutionUtils.getPage` for the count — and **delete `findFiltered`/
  `countFiltered` from `BookCrudRepository`**. Use the non-deprecated
  `findAll(Query, Class)` + `count(Query, Class)` pair; `findAll(Query, Class, Pageable)` is
  `@Deprecated(forRemoval = true)` since Spring Data 4.0. Apply postgres-patterns to the
  resulting query.
- [x] 6.4 (GREEN) Change `BookResource#list` to take `@PageableDefault(size = 20, sort =
  "id") Pageable` and return `PagedModel<BookDto>`; **delete `BookPageResponse.java`**
  and the `toResponse(BookPage)` mapping from `BookDtoMapper`. Reject sort properties
  outside the book's own with a `400` (springboot-patterns: a client error must not surface
  as a `500` from inside Spring Data).
- [x] 6.5 Narrow `ArchitectureTest.domain_is_free_of_infrastructure_technology` to exempt
  `org.springframework.data.domain..` while still banning the rest of
  `org.springframework.data..` and all web/servlet/JPA/Jackson packages (design D8).
- [x] 6.6 Run `./gradlew build` and confirm it passes with **zero NullAway errors** — the
  new Spring Data types are unannotated third-party APIs, so no `@SuppressWarnings` should
  be needed; if one is, document the justification. Confirm `ArchitectureTest` stays green,
  which is the guard that the D8 relaxation is narrow.

## 7. Refactor: align names with the hexagonal file-naming convention

- [x] 7.1 Move the three packages from `catalog` to `book` so `<domain_name>` = `book` and
  `<Domain>` = `Book` per `openspec/config.yaml`: `domain/catalog` → `domain/book`,
  `infra/spi/db/catalog` → `infra/spi/db/book`, `infra/api/rest/catalog` →
  `infra/api/rest/book` (production and mirrored test sources). The `catalog` *capability*
  (`specs/catalog/`) keeps its name — it is a business concept, not a package.
- [x] 7.2 Rename the types onto the convention's suffixes: `CatalogService` →
  `BookUsecase`, `BookRepository` → `BookPort`, `BookMapper` → `BookEntityMapper`,
  `BookDbAdapter` → `BookAdapter`, `BookRestMapper` → `BookDtoMapper` (`toResponse` →
  `toDto`), `BookResponse` → `BookDto`, `BookRequest` → `CreateBookRequest`,
  `CatalogExceptionHandler` → `BookExceptionHandler`. Rename the tests to match
  (`CatalogServiceTest` → `BookUsecaseTest`, `BookDbAdapterIT` → `BookAdapterIT`,
  `CatalogSchemaIT` → `BookSchemaIT`, moved beside the db adapter it exercises).
  No behaviour, HTTP contract, or SQL changes: routes, operationIds, JSON field names,
  `V1__create_books_table.sql`, and the `uq_books_isbn` constraint are untouched.
- [x] 7.3 Add naming rules to `ArchitectureTest` so the next domain cannot drift the same
  way: `role_types_are_named_after_their_package` (a role-suffixed type must be prefixed
  with the UpperCamelCase form of its package), plus `use_cases_are_named_usecase`,
  `domain_interfaces_are_ports`, `rest_controllers_are_named_resource`,
  `persistence_aggregates_are_named_entity`, `db_mappers_are_named_entity_mapper`,
  `rest_mappers_are_named_dto_mapper`; extend
  `driven_ports_are_implemented_by_driven_adapters` with the `*Adapter` suffix.
- [x] 7.4 Run `./gradlew clean build` (clean, so stale MapStruct `*Impl` classes from the
  old names cannot linger) and confirm zero NullAway errors and every ArchUnit rule green.
  Verify the new guard actually bites by temporarily renaming `BookUsecase` to
  `CatalogUsecase` and confirming `role_types_are_named_after_their_package` fails.
