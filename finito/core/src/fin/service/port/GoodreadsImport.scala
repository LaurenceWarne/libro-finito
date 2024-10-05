package fin.service.port

import java.time.LocalDate

import scala.util.Try

import cats.effect.kernel.Async
import cats.implicits._
import fs2.io.file.{Files, Path}
import fs2.text

import fin.DefaultCollectionNotSupportedError
import fin.Types._
import fin.service.search.BookInfoService

trait CollectionImport[F[_]] {
  def importResource(
      resource: String,
      collection: Option[String]
  ): F[Collection]
}

/** https://www.goodreads.com/review/import
  */
class GoodreadsImport[F[_]: Async: Files](
    maybeDefaultCollection: Option[String],
    infoService: BookInfoService[F]
) extends CollectionImport[F] {
  override def importResource(
      resource: String,
      collection: Option[String]
  ): F[Collection] = {
    val result = Files[F]
      .readAll(Path(resource))
      .through(text.utf8.decode)
      .through(text.lines)
      .tail
      .map(line => line.split(", "))
      .map { r =>
        for {
          (title, author, isbn) <- (r.lift(0), r.lift(1), r.lift(2)).tupled
          maybeRating           <- r.lift(3).map(_.toIntOption)
          maybeRead <- r.lift(9).map(st => Try(LocalDate.parse(st)).toOption)
          added     <- r.lift(10).map(LocalDate.parse)
        } yield GoodreadsCSVRow(
          title = title,
          authors = List(author),
          isbn = isbn,
          dateAdded = added.some,
          rating = maybeRating,
          lastRead = maybeRead
        ).toUserBook("", "")
      }
      .flattenOption
      .parEvalMapUnbounded { book =>
        val args =
          QueryBooksArgs(book.title.some, book.authors.headOption, None, None)
        infoService.search(args).map { ls =>
          ls.headOption.map(ub => book.copy(thumbnailUri = ub.thumbnailUri))
        }
      }
      .flattenOption
      .compile
      .toList
    for {
      books <- result
      collectionName <- Async[F].fromOption(
        collection.orElse(maybeDefaultCollection),
        DefaultCollectionNotSupportedError
      )
      collection = Collection(
        collectionName,
        books,
        Sort(SortType.Author, true),
        None
      )
    } yield collection
  }
}

final case class GoodreadsCSVRow(
    title: String,
    authors: List[String],
    isbn: String,
    dateAdded: Option[LocalDate],
    rating: Option[Int],
    lastRead: Option[LocalDate]
) {
  def toUserBook(description: String, thumbnailUri: String): UserBook =
    UserBook(
      title = title,
      authors = authors,
      description = description,
      isbn = isbn,
      thumbnailUri = thumbnailUri,
      dateAdded = dateAdded,
      rating = rating,
      startedReading = None,
      lastRead = lastRead,
      review = None
    )
}
