# Book Store

The catalog a bookstore keeps: the bibliographic records it carries and the ways a client
asks for them. This is the language the domain speaks; the words the frameworks use for the
same ideas are listed under _Avoid_ so they stay in the adapters.

## Language

**Book**:
A bibliographic record the store carries — one title by one author, identified by its ISBN.
_Avoid_: Title, item, product, publication

**ISBN**:
The industry identifier that distinguishes one book from every other. No two books share one.
_Avoid_: Barcode, reference, SKU

**Book Filter**:
What to narrow a book listing by. Every part of it is optional, and an omitted part means
"do not narrow by this" rather than "match nothing".
_Avoid_: Criteria, search criteria, query, specification
