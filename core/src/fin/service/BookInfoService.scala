package fin.service

import fin.Types._

trait BookInfoService[F[_]] {

  /**
    * Find books satisfying the specified arguments.
    *
    * @param booksArgs books arguments
    * @return books satisfying booksArgs
    */
  def search(booksArgs: QueriesBooksArgs): F[List[UserBook]]

  /**
    * Find a book given an isbn.
    *
    * @param bookArgs isbn data
    * @return a book with the given isbn
    */
  def fromIsbn(bookArgs: QueriesBookArgs): F[UserBook]
}
