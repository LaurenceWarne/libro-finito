package fin.service.book

import fin.Types._

trait SeriesInfoService[F[_]] {
  def series(args: QuerySeriesArgs): F[List[UserBook]]
}
