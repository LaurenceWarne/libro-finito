# Changelog

## v0.6.0

* [c4ab43f](https://github.com/LaurenceWarne/libro-finito/commit/c4ab43f6384c10ad556a9bf05cfb57ebfac011d5) Provisional support for looking up book series
* [7b6bf9b](https://github.com/LaurenceWarne/libro-finito/commit/7b6bf9b7826d9cf2c1de67a7f9883834174b8395) Slight improvment in the ordering of search results

## v0.5.0

* [aa62ace](https://github.com/LaurenceWarne/libro-finito/commit/aa62acee063d84c78419fbe29db82ca6e57dbacb): Add a special collection for read books
* [57180be](https://github.com/LaurenceWarne/libro-finito/commit/57180be031110c612e0b00d2b628cbe595274525): Migrate to CE3
* [0cc4ab9](https://github.com/LaurenceWarne/libro-finito/commit/0cc4ab9da4a2759a4fe3a4bd1d331a805ccb7abd): Make the "not found" image the same size as the rest of the thumbnails

## v0.4.2

* [89e6b12](https://github.com/LaurenceWarne/libro-finito/commit/89e6b1276edbd3427a4beb6f760d18bc03967808): Use hikari connection pool
* [b219a5e](https://github.com/LaurenceWarne/libro-finito/commit/b219a5e7015b81a65c00fe4a87fb052c1fe3352e): Ask for gzip responses from Google Books
* [2a73a07](https://github.com/LaurenceWarne/libro-finito/commit/2a73a072d5a58f11922a9119f3649e3616d269b6): Ask for partial responses from Google Books

## v0.4.1

* Fix bug when Google provides us no titles
* Return an informative error when `addBook` asks to add a book to a collection it's already in

## v0.4.0

* `book` query now returns multiple books
