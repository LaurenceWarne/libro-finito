package fin.service

trait BookInfoService[F[_]] {
  def search(bookArgs: BookArgs): F[List[Book]]
}
