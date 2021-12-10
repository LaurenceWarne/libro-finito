package fin.service.summary

import fin.Types._

trait MontageService[F[_]] {
  def montage(
      books: List[UserBook],
      maybeSpecification: Option[MontageInput]
  ): F[String]
}
