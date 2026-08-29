create table books (
    id             bigint generated always as identity primary key,
    isbn           varchar(20)  not null constraint uq_books_isbn unique,
    title          varchar(512) not null,
    author         varchar(256) not null,
    published_year integer,
    genre          varchar(128)
);
