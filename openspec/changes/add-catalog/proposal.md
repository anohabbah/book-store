## Why

The book-store is a fresh scaffold with no business domains yet. Before the lending
mechanics (copies, rentals, members) can be built, the system needs the bibliographic
foundation every other context depends on: a catalog of which books exist. Starting
catalog-only lets us prove the full hexagonal stack (REST → domain → Spring Data JDBC →
Flyway/Postgres) end-to-end against a thin, low-risk slice.

## What Changes

- Introduce a new `catalog` domain: a `Book` bibliographic record (id, isbn, title,
  author, publishedYear, genre) with **no** stock/availability/copies fields — those
  belong to the future `inventory` and `rental` contexts.
- Expose full CRUD over a REST API at `/books`:
  - `POST /books` — create (201 + `Location`; 400 invalid; 409 duplicate ISBN)
  - `GET /books/{id}` — fetch one (200 / 404)
  - `GET /books` — paginated list with optional `title` (contains), `author`, `isbn` filters
  - `PUT /books/{id}` — full replace (200 / 400 / 404)
  - `DELETE /books/{id}` — remove (204 / 404)
- Enforce **ISBN uniqueness** as the one real business rule, surfaced as `409 Conflict`.
- Add the first Flyway migration (`V1__create_books_table.sql` — Laravel-style
  description per project convention) creating a `books` table with a unique
  constraint on `isbn`.
- Wire the domain through the project's hexagonal layout: `domain/catalog/`,
  `infra/spi/db/catalog/`, `infra/api/rest/catalog/`.

## Capabilities

### New Capabilities
- `catalog`: managing the bibliographic record of books the store carries — creating,
  retrieving, listing/filtering, replacing, and deleting `Book` entries, with ISBN
  uniqueness enforced.

### Modified Capabilities
<!-- None — this is the first domain. -->

## Impact

- **New packages** (each gets a `@NullMarked` `package-info.java`):
  - `domain/catalog/` — `Book`, `CatalogService`, `BookRepository` (driven port)
  - `infra/spi/db/catalog/` — `BookEntity`, `BookMapper`, `BookDbAdapter`
  - `infra/api/rest/catalog/` — `BookResource`, `BookDto`s, `BookRestMapper`
- **Database**: new `books` table via
  `src/main/resources/db/migration/V1__create_books_table.sql`
  (first migration; directory is currently empty). `UNIQUE(isbn)`.
- **Dependencies**: none added — uses existing Spring Web MVC, Validation, Spring Data
  JDBC, Flyway, MapStruct, springdoc-openapi already on the classpath.
- **Tests**: `BookResourceIT` (and supporting slice tests) against the shared
  Testcontainers Postgres; NullAway must stay green (`./gradlew build`).
- **Deferred (explicitly out of scope)**: copies/inventory, availability, rentals,
  members, fees, multi-author modeling, soft delete. Hard delete is acceptable now since
  no foreign keys reference `books` yet; this becomes restrict/soft-delete once
  `inventory` arrives.
