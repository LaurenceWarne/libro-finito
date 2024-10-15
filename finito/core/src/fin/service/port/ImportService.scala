package fin.service.port

import cats.MonadThrow

import fin.Types._

trait ImportService[F[_]] {
  def importResource(args: MutationImportArgs): F[Int]
}

trait ApplicationImportService[F[_]] {
  def importResource(
      content: String,
      langRestrict: Option[String]
  ): F[Int]
}

/** https://www.goodreads.com/review/import
  */
class ImportServiceImpl[F[_]: MonadThrow](
    goodreadsImportService: ApplicationImportService[F]
) extends ImportService[F] {

  override def importResource(args: MutationImportArgs): F[Int] =
    args.importType match {
      case PortType.Goodreads =>
        goodreadsImportService.importResource(args.content, args.langRestrict)
      case PortType.Finito =>
        MonadThrow[F].raiseError(
          new Exception("Finito import not yet supported")
        )
    }
}

object ImportServiceImpl {
  def apply[F[_]: MonadThrow](
      goodreadsImportService: ApplicationImportService[F]
  ): ImportServiceImpl[F] =
    new ImportServiceImpl(goodreadsImportService)
}
