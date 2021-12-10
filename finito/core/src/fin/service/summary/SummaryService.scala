package fin.service.summary

import java.time.LocalDate

import fin.Types._

trait SummaryService[F[_]] {
  def summary(
      maybeFrom: Option[LocalDate],
      maybeTo: Option[LocalDate],
      maybeSpecification: Option[MontageInput]
  ): F[Summary]
}
