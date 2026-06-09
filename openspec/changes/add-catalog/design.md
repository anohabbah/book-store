## Context

The book-store is a fresh Spring Modulith scaffold (Java 25, Spring Boot 4, Spring Data
JDBC, Flyway/Postgres, MapStruct, NullAway in JSpecify mode) with no business domains
yet. This change introduces the first domain, `catalog`, as a vertical slice proving the
hexagonal stack end-to-end. It is deliberately CRUD-shaped; the genuinely stateful lending
logic (copies, rentals, members, fees) arrives in later domains and must not leak into the
catalog now.

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
- A `catalog` domain with a `Book` bibliographic record and full CRUD REST API at
  `/books` (create, get, paginated+filtered list, replace, delete).
- ISBN uniqueness enforced (→ `409`), input validation (→ `400`), missing-resource
  handling (→ `404`), all via RFC 7807 `ProblemDetail`.
- Clean Ports & Adapters wiring with the domain free of any infrastructure import.
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
  domain/catalog/
    package-info.java          # @NullMarked
    Book.java                  # record — domain object
    BookRepository.java        # driven port (interface), owned by the domain
    CatalogService.java        # @Service use case (class — behavioural, see D5)
    BookNotFoundException.java  # domain exception (class — extends RuntimeException, D5)
    DuplicateIsbnException.java # domain exception (class — extends RuntimeException, D5)
  infra/spi/db/catalog/
    package-info.java          # @NullMarked
    BookEntity.java            # record — Spring Data JDBC aggregate (@Table "books")
    BookCrudRepository.java     # Spring Data JDBC repository (interface, framework)
    BookMapper.java            # MapStruct: BookEntity ↔ Book
    BookDbAdapter.java         # @Component implements domain BookRepository
  infra/api/rest/catalog/
    package-info.java          # @NullMarked
    BookResource.java          # @RestController at /books
    BookRequest.java           # record — request DTO (create + replace share shape)
    BookResponse.java          # record — response DTO
    BookRestMapper.java        # MapStruct: BookRequest → Book, Book → BookResponse
    CatalogExceptionHandler.java # @RestControllerAdvice → ProblemDetail (404/409/400)

src/main/resources/db/migration/
    V1__create_books_table.sql # first migration (Laravel-style description)

src/test/java/dev/abbah/bookstore/infra/api/rest/catalog/
    BookResourceIT.java        # @SpringBootTest + Testcontainers Postgres (primary slice test)
src/test/java/dev/abbah/bookstore/domain/catalog/
    CatalogServiceTest.java    # unit/slice test of use-case rules (port stubbed)
```

`domain/catalog/BookRepository` is the **port**; `infra/spi/db/catalog/BookDbAdapter`
implements it and delegates to the framework `BookCrudRepository`. The domain never sees
`BookEntity`, `BookCrudRepository`, or any Spring Data type.

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
- `BookResponse(Long id, ...)` — id is **non-null** here: a response only ever describes a
  persisted book.
- No `@Contract` is warranted — these are plain records/mappers/CRUD, not reusable
  assertion/predicate/transform helpers (the skill notes `@Contract` concentrates in
  low-level utilities, and ordinary service/web code relies on plain `@Nullable`).

### D4 — ISBN uniqueness: check-in-service, DB constraint as backstop

`CatalogService.create` and `.replace` call `BookRepository.existsByIsbn(...)` (excluding
self on replace) and throw `DuplicateIsbnException` when violated. The `UNIQUE(isbn)`
constraint is the race-condition backstop: `BookDbAdapter` translates Spring's
`DbActionExecutionException`/`DataIntegrityViolationException` into the same
`DuplicateIsbnException`, so the API contract (`409`) holds regardless of path.

### D5 — Records vs classes; where logic lives

Data carriers are records: `Book`, `BookEntity`, `BookRequest`, `BookResponse`.
Behavioural types are classes with a documented reason:
- `CatalogService` — a `@Service` holding orchestration/rules; not data.
- `BookNotFoundException`, `DuplicateIsbnException` — must extend `RuntimeException`
  (framework/JDK constraint: exceptions cannot be records).

Controllers stay thin (delegate to `CatalogService`); the service is `@Transactional`
(`readOnly = true` on queries). Constructor injection throughout. MapStruct mappers use
the Spring component model so they are injectable `@Component`s.

### D6 — REST contract and listing

`BookResource` maps the five verbs in the spec. Listing uses Spring Data pagination:
`GET /books?page=&size=&title=&author=&isbn=` → `Page<BookResponse>`. Filters combine
conjunctively. Because Spring Data JDBC has no Query-by-Example/Specifications, the
adapter resolves the supplied filter combination to an explicit `@Query` (or a small set
of derived finders) on `BookCrudRepository`; `title` uses case-insensitive `ILIKE
'%' || :title || '%'`, `author`/`isbn` use equality. Errors are RFC 7807 `ProblemDetail`
via `CatalogExceptionHandler` (`spring.mvc.problemdetails.enabled` defaults on in Boot 4):
`DuplicateIsbnException`→409, `BookNotFoundException`→404,
`MethodArgumentNotValidException`→400.

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
  query handling. → The filter set is fixed and small (title/author/isbn); an explicit
  `@Query` (or derived finders) is sufficient and clearer than a criteria builder.

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
