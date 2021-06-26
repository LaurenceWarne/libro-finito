package fin.service

import fin.Types._

trait BookInfoService[F[_]] {
  def search(booksArgs: QueriesBooksArgs): F[List[Book]]
  def fromIsbn(bookArgs: QueriesBookArgs): F[Book]
}
