package fin.service.port

import fin.Types._

/** https://www.goodreads.com/review/import
  */
trait CollectionExportService[F[_]] {
  def exportCollection(exportArgs: QueryExportArgs): F[String]
}
