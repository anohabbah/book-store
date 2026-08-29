## Context

The book-store is a fresh Spring Modulith scaffold (Java 25, Spring Boot 4, Spring Data
JDBC, Flyway/Postgres, MapStruct, NullAway in JSpecify mode) with no business domains
yet. This change introduces the first domain, `book` (capability `catalog`), as a vertical
slice proving the hexagonal stack end-to-end. It is deliberately CRUD-shaped; the genuinely
stateful lending logic (copies, rentals, members, fees) arrives in later domains and must
not leak into the catalog now.

Constraints that shape the design:
- Hexagonal layout is mandatory: `domain/` is pure (no infra imports), `infra/` depends
  inward only.
- Persistence is Spring Data JDBC (aggregates + repositories), **not** JPA — so custom
  finders use SQL `@Query`, and there is no lazy loading or entity manager.
- Every environment uses PostgreSQL; schema changes go through Flyway. `db/migration` is
  empty today, so the first migration is `V1__`.
- NullAway fails the build on nullness violations; every new package needs an
  `@NullMarked` `package-info.java`.
- Cross-layer mapping uses MapStruct (Spring component model); domain objects, entities,
  and DTOs are Java records.

## Goals / Non-Goals

**Goals:**
- A `book` domain with a `Book` bibliographic record and full CRUD REST API at
  `/v1/books` (create, get, paginated+filtered list, replace, delete).
- ISBN uniqueness enforced (→ `409`), input validation (→ `400`), missing-resource
  handling (→ `404`), all via RFC 7807 `ProblemDetail`.
- Clean Ports & Adapters wiring with the domain free of persistence, web, and JSON
  technology. Spring Data's pagination value types (`Page`, `Pageable`, `Sort`) are the one
  deliberate exception — see D8.
- Integration test coverage against the shared Testcontainers Postgres; green NullAway.

**Non-Goals:**
- Copies / inventory / availability, rentals, members, fees (future domains).
- Multi-author modeling, descriptions, cover images, genre enums (single `author` string;
  `genre` free text).
- Soft delete (hard delete now — no FKs reference `books` yet).
- AuthN/AuthZ, rate limiting (no security starter on the classpath; out of scope here).
- Full-text / trigram search (filter is simple `ILIKE` substring; see Risks).

## Decisions

### D1 — Package layout and the concrete files

Every production file, placed in the hexagonal layout, dependencies flowing inward only
(`infra/api/rest → domain ← infra/spi/db`; `domain` imports nothing from `infra`):

```
src/main/java/dev/abbah/bookstore/
  domain/book/
    package-info.java          # @NullMarked
    Book.java                  # record — domain object
    BookPort.java              # driven port (interface), owned by the domain
    BookUsecase.java           # @Service use case (class — behavioural, see D5)
    BookNotFoundException.java  # domain exception (class — extends RuntimeException, D5)
    DuplicateIsbnException.java # domain exception (class — extends RuntimeException, D5)
  infra/spi/db/book/
    package-info.java          # @NullMarked
    BookEntity.java            # record — Spring Data JDBC aggregate (@Table "books")
    BookCrudRepository.java     # Spring Data JDBC repository (interface, framework)
    BookEntityMapper.java      # MapStruct: BookEntity ↔ Book
    BookAdapter.java           # @Component implements domain BookPort
  infra/api/rest/book/
    package-info.java          # @NullMarked
    BookResource.java          # @RestController at /v1/books
    CreateBookRequest.java     # record — request DTO (create + replace share shape)
    BookDto.java               # record — response DTO
    BookDtoMapper.java         # MapStruct: request → Book, Book → BookDto
    BookExceptionHandler.java  # @RestControllerAdvice → ProblemDetail (404/409/400)

src/main/resources/db/migration/
    V1__create_books_table.sql # first migration (Laravel-style description)

src/test/java/dev/abbah/bookstore/infra/api/rest/book/
    BookResourceIT.java        # @SpringBootTest + Testcontainers Postgres (primary slice test)
src/test/java/dev/abbah/bookstore/infra/spi/db/book/
    BookAdapterIT.java         # adapter round-trip, filters, UNIQUE(isbn) backstop
    BookSchemaIT.java          # Flyway/schema smoke test
src/test/java/dev/abbah/bookstore/domain/book/
    BookUsecaseTest.java       # unit/slice test of use-case rules (port stubbed)
```

The Java package is `book`, not `catalog`: `openspec/config.yaml` derives every file name
from the package (`<domain_name>` → `<Domain>`), so the package that holds `Book`,
`BookEntity`, and `BookResource` must be `book`. `catalog` remains the name of the
*capability* (`specs/catalog/`), which is a business concept, not a package.

`ArchitectureTest` enforces this rather than leaving it to review: any type carrying a
role suffix (`Usecase`, `Port`, `Entity`, `EntityMapper`, `Adapter`, `Resource`, `Dto`,
`DtoMapper`, and the messaging suffixes) must be prefixed with the UpperCamelCase form of
its own package, and the role suffixes themselves are pinned to their position
(`@Service` → `*Usecase` in the domain, `@RestController` → `*Resource`, `@Table` →
`*Entity`, a domain interface → `*Port`, a mapper → `*EntityMapper`/`*DtoMapper`). Types
the convention does not name — `Book` itself, the exceptions, `CreateBookRequest`,
`BookCrudRepository`, `BookExceptionHandler` — carry no role suffix and are unconstrained.

`domain/book/BookPort` is the **port**; `infra/spi/db/book/BookAdapter`
implements it and delegates to the framework `BookCrudRepository`/`JdbcAggregateOperations`.
The domain never sees `BookEntity`, `BookCrudRepository`, or any Spring Data *persistence*
type; it does speak `Page`/`Pageable` (D8).

### D2 — Identity: surrogate id + `BookEntity` as the aggregate

`Book` and `BookEntity` carry a server-generated surrogate `Long id` with a separate
`UNIQUE(isbn)` constraint, rather than ISBN-as-PK. Rationale: Spring Data JDBC aggregates
work best with a generated `@Id`, and future `inventory` copies will FK to a stable
numeric book id rather than a mutable ISBN.

- Alternative considered: ISBN as natural PK — rejected; couples child tables to a
  business-mutable key and complicates corrections.

### D3 — Null-safety of the id and optional fields (jspecify-spring-framework-patterns)

Applying the inference heuristics: a not-yet-persisted `Book`/`BookEntity` legitimately
has **no** id (it is assigned by the database on insert and is genuinely absent before
that). Per the skill's decision rule — *legitimately absent → `@Nullable`; non-null but
deferred-and-certain → `NullAway.Init`* — the id is the **`@Nullable`** case, not
`@SuppressWarnings("NullAway.Init")`.

- `Book(@Nullable Long id, String isbn, String title, String author,
  @Nullable Integer publishedYear, @Nullable String genre)` — `isbn/title/author`
  non-null (JSpecify default under `@NullMarked`); `publishedYear`, `genre` `@Nullable`.
- `BookEntity(@Id @Nullable Long id, String isbn, String title, String author,
  @Nullable Integer publishedYear, @Nullable String genre)` — same nullability.
- `BookDto(Long id, ...)` — id is **non-null** here: a response only ever describes a
  persisted book.
- No `@Contract` is warranted — these are plain records/mappers/CRUD, not reusable
  assertion/predicate/transform helpers (the skill notes `@Contract` concentrates in
  low-level utilities, and ordinary service/web code relies on plain `@Nullable`).

### D4 — ISBN uniqueness: check-in-service, DB constraint as backstop

`BookUsecase.create` and `.replace` call `BookPort.existsByIsbn(...)` (excluding
self on replace) and throw `DuplicateIsbnException` when violated. The `UNIQUE(isbn)`
constraint is the race-condition backstop: `BookAdapter` translates Spring's
`DbActionExecutionException`/`DataIntegrityViolationException` into the same
`DuplicateIsbnException`, so the API contract (`409`) holds regardless of path.

### D5 — Records vs classes; where logic lives

Data carriers are records: `Book`, `BookEntity`, `CreateBookRequest`, `BookDto`.
Behavioural types are classes with a documented reason:
- `BookUsecase` — a `@Service` holding orchestration/rules; not data.
- `BookNotFoundException`, `DuplicateIsbnException` — must extend `RuntimeException`
  (framework/JDK constraint: exceptions cannot be records).

Controllers stay thin (delegate to `BookUsecase`); the service is `@Transactional`
(`readOnly = true` on queries). Constructor injection throughout. MapStruct mappers use
the Spring component model so they are injectable `@Component`s.

### D6 — REST contract and listing

`BookResource` maps the five verbs in the spec. Listing uses Spring Data pagination
end-to-end: `GET /v1/books?page=&size=&sort=&title=&author=&isbn=` binds to a resolved
`Pageable` (`@PageableDefault(size = 20, sort = "id")`, resolver supplied by
`SpringDataWebAutoConfiguration`) and returns `PagedModel<BookDto>` — Spring Data's own
stable envelope (`content` + `page:{size,number,totalElements,totalPages}`), so no bespoke
response record is maintained. Filters combine conjunctively. Because Spring Data JDBC has
no Specifications *and its string-based `@Query` cannot accept a `Pageable`/`Sort`/`Limit`*,
the adapter builds a programmatic `Criteria` and runs it through `JdbcAggregateOperations`:
`title` uses `Criteria.where("title").like("%…%").ignoreCase(true)`, `author`/`isbn` use
equality; `Query.with(pageable)` applies limit/offset/sort and `PageableExecutionUtils`
assembles the `Page`. `sort` is client-controlled, so `BookResource` rejects any property
outside the book's own (`id`, `isbn`, `title`, `author`, `publishedYear`, `genre`) with a
`400` rather than letting Spring Data fail with a `500`. Errors are RFC 7807 `ProblemDetail`
via `BookExceptionHandler` (`spring.mvc.problemdetails.enabled` defaults on in Boot 4):
`DuplicateIsbnException`→409, `BookNotFoundException`→404,
`MethodArgumentNotValidException`→400.

Routes are versioned through Spring's API versioning rather than a bare path prefix:
`@RequestMapping(path = "/v{version}/books", version = "1")` on `BookResource`, paired
with `spring.mvc.apiversion.use.path-segment: 0` (default `"1"`, not required) in
`application.yaml` — matching the `/v1/bootui` and `/v1/scalar` convention already used
by the dev tooling. `consumes` is declared on `@PostMapping`/`@PutMapping` only, never at
class level: on the class it would also apply to `GET`/`DELETE`, which carry no
`Content-Type` and would be rejected with `415`.

### D7 — Migration `V1__create_books_table.sql` (postgres-patterns)

First migration (directory empty today). Filename keeps Flyway's sequential `V<N>__`
version with a Laravel (Eloquent)-style snake_case description
(`create_<table>_table`), per project convention. Table `books`:

| column          | type                                  | notes                    |
|-----------------|---------------------------------------|--------------------------|
| `id`            | `bigint generated always as identity` | primary key (surrogate)  |
| `isbn`          | `varchar(20) not null`                | `constraint uq_books_isbn unique` |
| `title`         | `varchar(512) not null`               |                          |
| `author`        | `varchar(256) not null`               |                          |
| `published_year`| `integer`                             | nullable                 |
| `genre`         | `varchar(128)`                        | nullable                 |

The `UNIQUE(isbn)` constraint provides the lookup index for the `isbn` filter and the
uniqueness backstop (D4). `BookEntity` field names map to snake_case columns
(`publishedYear`→`published_year`) per Spring Data JDBC's default `NamingStrategy`.

### D8 — Spring Data's pagination types are the domain's paging vocabulary

The port and the use case speak `org.springframework.data.domain.Page`/`Pageable` directly:

```java
Page<Book> findAll(@Nullable String title, @Nullable String author,
                   @Nullable String isbn, Pageable pageable);   // BookPort
```

The alternative — a hand-rolled `BookPage(List<Book>, int page, int size, long
totalElements)` in `domain/book`, mapped to a `BookPageResponse` at the REST edge — was
built first and then removed. It bought purity at the cost of a value object, a MapStruct
mapping, and hand-computed `limit`/`offset`/count in the adapter, all re-implementing what
`spring-data-commons` already ships.

`Page`/`Pageable`/`Sort` are plain value types, not a persistence technology: they carry no
connection, no session, and no SQL. Depending on them costs the domain nothing a
`java.util.List` would not. `ArchitectureTest.domain_is_free_of_infrastructure_technology`
is therefore narrowed to exempt `org.springframework.data.domain..` while still banning the
rest of `org.springframework.data..` (repositories, JDBC, relational mapping) along with
web, servlet, JPA, and Jackson — so `BookEntity`, `BookCrudRepository`, and
`JdbcAggregateOperations` remain firmly out of the domain.

## Risks / Trade-offs

- **`ILIKE '%term%'` substring filter does not use the btree index → full scan on large
  catalogs.** → Acceptable at slice-one volume. Mitigation when it matters: add a
  `pg_trgm` GIN index on `lower(title)` in a later migration; deferred now to keep the
  slice minimal.
- **Hard delete will break once `inventory`/`rental` FK to `books`.** → Documented
  non-goal; revisited (restrict or soft-delete) when those domains land.
- **Check-then-act ISBN uniqueness has a TOCTOU window.** → Mitigated by the DB `UNIQUE`
  constraint and adapter-level translation to `409` (D4); no correctness gap, only a
  redundant pre-check for a friendlier common-path error.
- **Spring Data JDBC lacks dynamic Specifications**, so combined filters need explicit
  query handling. → The filter set is fixed and small (title/author/isbn); a programmatic
  `Criteria` executed through `JdbcAggregateOperations` covers it, and unlike a string
  `@Query` it accepts the request's `Pageable` (D6).
- **The domain now depends on `spring-data-commons`.** → Accepted and fenced by ArchUnit
  (D8): only `org.springframework.data.domain..` is allowed through.

## Migration Plan

1. Add `V1__create_books_table.sql`; Flyway applies it automatically on startup (and in
   the Testcontainers Postgres used by `BookResourceIT`).
2. No data backfill — first table, empty.
3. Rollback: the change is additive and isolated (new table, new packages). Reverting the
   code and dropping `books` fully removes it; nothing else references it yet.

## Open Questions

- None blocking. Future call (not this slice): whether the eventual `inventory` FK uses
  `ON DELETE RESTRICT` plus soft-delete on `books`, or hard-delete is forbidden once
  copies exist — decided when `inventory` is designed.
