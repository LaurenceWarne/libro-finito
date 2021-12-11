package fin.service.summary

import fin.Types._

trait SummaryService[F[_]] {
  def summary(args: QueriesSummaryArgs): F[Summary]
}
