package fin.api

trait BookAPI[F[_]] {
  def search(bookArgs: BookArgs): F[List[Book]]
}
