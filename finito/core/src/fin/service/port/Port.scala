package fin.service.port

import fin.Types._
import fs2.text
import fs2.io.file.{Files, Path}
import cats.effect.kernel.Async
import cats.implicits._
import java.time.LocalDate
import scala.util.Try

trait CollectionImport[F[_]] {
  def importResource(resource: String): F[Collection]
}

/**
  * https://www.goodreads.com/review/import
  */
class GoodreadsImport[F[_]: Async] extends CollectionImport[F] {
  override def importResource(resource: String): F[Collection] = {
    val result = Files[F]
      .readAll(Path(resource))
      .through(text.utf8.decode)
      .through(text.lines)
      .tail
      .map(line => line.split(", "))
      .map { r =>
        for {
          title       <- r.lift(0)
          author      <- r.lift(1)
          isbn        <- r.lift(2)
          maybeRating <- r.lift(3).map(_.toIntOption)
          maybeRead   <- r.lift(9).map(st => Try(LocalDate.parse(st)).toOption)
          added       <- r.lift(10).map(LocalDate.parse)
        } yield GoodreadsCSVRow(
          title = title,
          authors = List(author),
          isbn = isbn,
          dateAdded = added.some,
          rating = maybeRating,
          lastRead = maybeRead
        ).toUserBook("", "")
      }
      .collect {
        case Some(row) => row
      }
      .compile
      .toList
    result.map(
      Collection("foobar", _, Sort(SortType.Author, true))
    )
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
      lastRead = lastRead
    )
}
