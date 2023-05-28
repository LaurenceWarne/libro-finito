package fin.service.port

import cats.effect.kernel.Async
import cats.implicits._

import fin.DefaultCollectionNotSupportedError
import fin.Types._
import fin.service.collection._

/**
  * https://www.goodreads.com/review/import
  */
trait CollectionExportService[F[_]] {
  def exportCollection(exportArgs: QueriesExportArgs): F[String]
}

class GoodreadsExportService[F[_]: Async](
    maybeDefaultCollection: Option[String],
    collectionService: CollectionService[F]
) extends CollectionExportService[F] {

  private val firstRow =
    "Title, Author, ISBN, My Rating, Average Rating, Publisher, Binding, Year Published, Original Publication Year, Date Read, Date Added, Bookshelves, My Review"

  override def exportCollection(exportArgs: QueriesExportArgs): F[String] = {
    for {
      collection <- Async[F].fromOption(
        exportArgs.collection.orElse(maybeDefaultCollection),
        DefaultCollectionNotSupportedError
      )
      collection <-
        collectionService.collection(QueriesCollectionArgs(collection, None))
      rows = collection.books.map { book =>
        show"""|${book.title.replaceAll(",", "")},
               |${book.authors.mkString(" ")},
               |${book.isbn},
               |${book.rating.fold("")(_.toString)},,,,,,
               |${book.lastRead.fold("")(_.toString)},
               |${book.dateAdded.fold("")(_.toString)},,""".stripMargin
          .replace("\n", "")
      }
    } yield (firstRow :: rows).mkString("\n")
  }
}

object GoodreadsExportService {
  def apply[F[_]: Async](
      maybeDefaultCollection: Option[String],
      collectionService: CollectionService[F]
  ) = new GoodreadsExportService[F](maybeDefaultCollection, collectionService)
}
