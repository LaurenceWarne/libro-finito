# Changelog

## v0.4.2

* [89e6b12](https://github.com/LaurenceWarne/libro-finito/commit/89e6b1276edbd3427a4beb6f760d18bc03967808): Use hikari connection pool
* [b219a5e](https://github.com/LaurenceWarne/libro-finito/commit/b219a5e7015b81a65c00fe4a87fb052c1fe3352e): Ask for gzip responses from Google Books
* [2a73a07](https://github.com/LaurenceWarne/libro-finito/commit/2a73a072d5a58f11922a9119f3649e3616d269b6): Ask for partial responses from Google Books

## v0.4.1

* Fix bug when Google provides us no titles
* Return informative error when `addBook` asks to add a book to a collection it's already in

## v0.4.0

* `book` query now returns multiple books
