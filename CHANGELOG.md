# Changelog

## v0.8.3

* [fc74700](https://github.com/LaurenceWarne/libro-finito/commit/fc74700ce2662d7765237e65a3f569f95ce2621f): Fix over-permissive address binding

## v0.8.2

* [e083fe8](https://github.com/LaurenceWarne/libro-finito/commit/644b6b6320e323f1fd90258d36ba96ba7351c154): Improve book series lookups

## v0.8.1

* [8107ace](https://github.com/LaurenceWarne/libro-finito/commit/2c22e99edc4108ae8ed544401938311c29cd3d4b): Make Goodreads import less error prone
* [8107ace](https://github.com/LaurenceWarne/libro-finito/commit/8107acef724cbf2419172755a51fd5801768efc7): Backup the DB whenever the service starts

## v0.8.0

* [1a5c684](https://github.com/LaurenceWarne/libro-finito/commit/1a5c6849b695a55f4333e86f5966b2d711c47d18): Implement Goodreads import

## v0.7.5

* [635701c](https://github.com/LaurenceWarne/libro-finito/commit/635701c7af61ef2510157729b782c0520b36d4e7): Add `includeAdded` parameter to `summary` query to allow showing only read books in montage images
* [88623de](https://github.com/LaurenceWarne/libro-finito/commit/88623de9efdd629926bcedf2e2fc05c4e674d5a9): Improve server performance after periods of inactivity

## v0.7.4

* [d3321fe](https://github.com/LaurenceWarne/libro-finito/commit/d3321fed2b53a1ecfcc003c58a990588204e297b): Implement pagination for collections

## v0.7.3

* [f7075ff](https://github.com/LaurenceWarne/libro-finito/commit/f7075ffa9930155aedb4f256e67c547011bfab1f): Update dependencies and log info about the server at startup

## v0.7.2

* [fa8d24e](https://github.com/LaurenceWarne/libro-finito/commit/fa8d24e8ee480850fe248bbe9c233475770805d5): Improve performance by keeping the server warm

## v0.7.1

* [a1d8951](https://github.com/LaurenceWarne/libro-finito/commit/a1d8951caf1f894408bdfcb1082b76c6079c17cc): Performance tidbits

## v0.7.0

* [de8f239](https://github.com/LaurenceWarne/libro-finito/commit/de8f239ad7e45a1af41a7d3caa34eb42020a8d67): Support for (yearly) summaries
* [4d0b1c8](https://github.com/LaurenceWarne/libro-finito/commit/4d0b1c81dc548ea49188ec031a1ef9d5143bf65e): Read books are sorted by last read in descending order by default

## v0.6.2

* [49b4300](https://github.com/LaurenceWarne/libro-finito/commit/49b43001f90229e731279e3e5f34aea6f1146ce4): Fix Schema scalars mis-translated

## v0.6.1

* [384c0ef](https://github.com/LaurenceWarne/libro-finito/commit/384c0efbf5ba46303c8bd91c186808da168ab15c): Streamline logging

## v0.6.0

* [c4ab43f](https://github.com/LaurenceWarne/libro-finito/commit/c4ab43f6384c10ad556a9bf05cfb57ebfac011d5): Provisional support for looking up book series
* [7b6bf9b](https://github.com/LaurenceWarne/libro-finito/commit/7b6bf9b7826d9cf2c1de67a7f9883834174b8395): Slight improvment in the ordering of search results

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
