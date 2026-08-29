## ADDED Requirements

### Requirement: Add a book to the catalog

The system SHALL allow a client to add a `Book` to the catalog via `POST /v1/books` with a
JSON body containing `isbn`, `title`, `author`, and optionally `publishedYear` and
`genre`. On success the system SHALL persist the book with a server-generated id and
return `201 Created` with the created book and a `Location` header pointing at the new
resource.

#### Scenario: Successful creation

- **WHEN** a client POSTs a valid book (unique ISBN, non-blank title and author)
- **THEN** the system returns `201 Created`, a `Location` header of `/v1/books/{id}`, and a
  body containing the generated `id` and the submitted fields

#### Scenario: Missing or invalid required fields

- **WHEN** a client POSTs a book with a blank/absent `isbn`, `title`, or `author`
- **THEN** the system returns `400 Bad Request` and does not persist anything

#### Scenario: Duplicate ISBN rejected

- **WHEN** a client POSTs a book whose `isbn` already exists in the catalog
- **THEN** the system returns `409 Conflict` and does not create a second record

### Requirement: Retrieve a book by id

The system SHALL return a single book via `GET /v1/books/{id}`.

#### Scenario: Book exists

- **WHEN** a client GETs `/v1/books/{id}` for an existing id
- **THEN** the system returns `200 OK` with the book's full representation

#### Scenario: Book does not exist

- **WHEN** a client GETs `/v1/books/{id}` for an id that is not in the catalog
- **THEN** the system returns `404 Not Found`

### Requirement: List and filter books

The system SHALL return a paginated list of books via `GET /v1/books`, accepting `page`,
`size`, and `sort` parameters, and SHALL carry the results in Spring Data's pagination
envelope (a `content` array plus a `page` object holding `size`, `number`, `totalElements`,
and `totalPages`). It SHALL support optional filtering by `title` (case-insensitive
substring match), `author` (exact match), and `isbn` (exact match). When multiple filters
are supplied they SHALL be combined conjunctively (AND).

#### Scenario: Paginated listing

- **WHEN** a client GETs `/v1/books?page=0&size=20`
- **THEN** the system returns `200 OK` with at most 20 books in `content` and pagination
  metadata under `page`

#### Scenario: Sorted listing

- **WHEN** a client GETs `/v1/books?sort=title,desc`
- **THEN** the system returns `200 OK` with the books ordered by title, descending

#### Scenario: Listing sorted by an unknown property

- **WHEN** a client GETs `/v1/books?sort=bogus`
- **THEN** the system returns `400 Bad Request`

#### Scenario: Filter by title substring

- **WHEN** a client GETs `/v1/books?title=dune`
- **THEN** the system returns only books whose title contains `dune`, case-insensitively

#### Scenario: Filter by exact ISBN

- **WHEN** a client GETs `/v1/books?isbn=<isbn>`
- **THEN** the system returns the matching book, or an empty page if none matches

### Requirement: Replace a book

The system SHALL replace an existing book's mutable fields via `PUT /v1/books/{id}` with a
complete book body. ISBN uniqueness SHALL continue to hold across the replacement.

#### Scenario: Successful replacement

- **WHEN** a client PUTs a valid, complete body to `/v1/books/{id}` for an existing id
- **THEN** the system returns `200 OK` with the updated book

#### Scenario: Replace a non-existent book

- **WHEN** a client PUTs to `/v1/books/{id}` for an id that is not in the catalog
- **THEN** the system returns `404 Not Found`

#### Scenario: Replacement that collides with another book's ISBN

- **WHEN** a client PUTs a body whose `isbn` already belongs to a different book
- **THEN** the system returns `409 Conflict` and leaves both books unchanged

#### Scenario: Replacement with invalid fields

- **WHEN** a client PUTs a body with a blank/absent `isbn`, `title`, or `author`
- **THEN** the system returns `400 Bad Request` and leaves the book unchanged

### Requirement: Delete a book

The system SHALL delete a book via `DELETE /v1/books/{id}`.

#### Scenario: Successful deletion

- **WHEN** a client DELETEs `/v1/books/{id}` for an existing id
- **THEN** the system returns `204 No Content` and the book is no longer retrievable

#### Scenario: Delete a non-existent book

- **WHEN** a client DELETEs `/v1/books/{id}` for an id that is not in the catalog
- **THEN** the system returns `404 Not Found`

### Requirement: Catalog excludes lending concerns

The `Book` representation SHALL contain only bibliographic data (`id`, `isbn`, `title`,
`author`, `publishedYear`, `genre`). It SHALL NOT expose stock, availability, copy counts,
or rental state; those concerns belong to future inventory and rental capabilities.

#### Scenario: Book representation has no availability fields

- **WHEN** a client retrieves any book
- **THEN** the response contains no stock, availability, copy-count, or rental fields
