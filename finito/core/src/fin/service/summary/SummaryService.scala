package fin.service.summary

import fin.Types._

trait SummaryService[F[_]] {
  def summary(args: QuerySummaryArgs): F[Summary]
}
