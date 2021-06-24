package fin.service

import fin.Types._

trait BookInfoService[F[_]] {
  def search(bookArgs: QueriesBooksArgs): F[List[Book]]
}
