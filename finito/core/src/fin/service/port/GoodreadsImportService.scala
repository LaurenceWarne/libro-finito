package fin.service.port

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import scala.concurrent.duration._

import cats.effect._
import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.implicits._
import fs2.Fallible
import fs2.data.csv._
import fs2.data.csv.generic.semiauto._
import org.typelevel.log4cats.Logger

import fin.BookConversions._
import fin.Types._
import fin.service.book._
import fin.service.collection._
import fin.service.search.BookInfoService

/** https://www.goodreads.com/review/import
  */
class GoodreadsImportService[F[_]: Async: Logger](
    bookInfoService: BookInfoService[F],
    collectionService: CollectionService[F],
    bookManagementService: BookManagementService[F]
) extends ApplicationImportService[F] {

  private val parallelism = 1
  private val timer       = Temporal[F]

  override def importResource(
      content: String,
      langRestrict: Option[String]
  ): F[Int] = {
    val result =
      fs2.Stream
        .emit(content.replace("\\n", "\n").replace("\\\"", "\""))
        .covary[Fallible]
        .through(decodeSkippingHeaders[GoodreadsCSVRow]())
        .compile
        .toList
    for {
      _ <- Logger[F].debug(s"Received ${content.length} chars worth of content")
      rows      <- Async[F].fromEither(result)
      userBooks <- createBooks(rows, langRestrict)
      bookShelfMap     = rows.map(row => row.isbn -> row.bookshelves).toMap
      inputBookshelves = bookShelfMap.values.flatten
      existingCollections <- collectionService.collections.map { ls =>
        ls.map(_.name).toSet
      }
      collectionsToCreate = inputBookshelves.filterNot { shelf =>
        existingCollections.contains(shelf)
      }
      _ <- collectionService.createCollections(collectionsToCreate.toSet)

      _ <- userBooks
        .flatMap { b =>
          bookShelfMap.getOrElse(b.isbn, Set.empty).toList.tupleLeft(b)
        }
        .traverse { case (book, shelf) =>
          collectionService.addBookToCollection(
            MutationAddBookArgs(Some(shelf), toBookInput(book))
          )
        }
    } yield userBooks.length
  }

  private def createBooks(
      rows: List[GoodreadsCSVRow],
      langRestrict: Option[String]
  ): F[List[UserBook]] = {
    for {
      userBooks <- rows.parTraverseN(parallelism) { row =>
        bookInfoService
          .search(
            QueryBooksArgs(
              titleKeywords = Some(row.title),
              authorKeywords = Some(row.author),
              maxResults = Some(5),
              langRestrict = langRestrict
            )
          )
          .map { books =>
            books.headOption.fold(row.toUserBook("", "")) { book =>
              row.toUserBook(book.description, book.thumbnailUri)
            }
          } <* Logger[F].info(
          s"Succeeded obtaining extra information for: ${row.title}"
        ) *> timer.sleep(500.millis)
      }
      _ <- bookManagementService.createBooks(userBooks)
    } yield userBooks
  }
}

object GoodreadsImportService {
  def apply[F[_]: Async: Logger](
      bookInfoService: BookInfoService[F],
      collectionService: CollectionService[F],
      bookManagementService: BookManagementService[F]
  ): GoodreadsImportService[F] =
    new GoodreadsImportService(
      bookInfoService,
      collectionService,
      bookManagementService
    )
}

// Book Id,Title,Author,Author l-f,Additional Authors,ISBN,ISBN13,My Rating,Average Rating,Publisher,Binding,Number of Pages,Year Published,Original Publication Year,Date Read,Date Added,Bookshelves,Bookshelves with positions,Exclusive Shelf,My Review,Spoiler,Private Notes,Read Count,Owned Copies
// 13450209,"Gardens of the Moon (The Malazan Book of the Fallen, #1)",Steven Erikson,"Erikson, Steven",,"=""1409083101""","=""9781409083108""",5,3.92,Transworld Publishers,ebook,768,2009,1999,2023/01/13,2023/01/10,"favorites, fantasy, fiction","favorites (#2), fantasy (#2), fiction (#3)",read,,,,1,0
final case class GoodreadsCSVRow(
    goodreadsBookId: Int,
    title: String,
    author: String,
    authorLf: String,
    additionalAuthors: Option[String],
    isbn: String,
    isbn13: Option[String],
    rating: Option[Int],
    averageRating: Float,
    publisher: Option[String],
    binding: String,
    numPages: Option[String],
    yearPublished: Option[Int],
    originalPublicationYear: Option[Int],
    dateRead: Option[LocalDate],
    dateAdded: LocalDate,
    bookshelvesStr: String,
    bookshelvesWithPositions: String,
    exclusiveShelf: String,
    myReview: Option[String],
    spoiler: String,
    privateNotes: Option[String],
    readCount: String,
    ownedCopies: String
) {

  def toUserBook(description: String, thumbnailUri: String): UserBook =
    UserBook(
      title = title,
      authors = author :: additionalAuthors.fold(List.empty[String])(
        _.split(", ").toList
      ),
      description = description,
      isbn = isbn,
      thumbnailUri = thumbnailUri,
      dateAdded = Some(dateAdded),
      rating = rating,
      startedReading = None,
      lastRead = dateRead,
      review = myReview
    )

  def bookshelves: Set[String] =
    bookshelvesStr.split(", ").toSet + exclusiveShelf
}

object GoodreadsCSVRow {
  implicit val localDateDecoder: CellDecoder[LocalDate] =
    CellDecoder.stringDecoder.emap { s =>
      Either
        .catchNonFatal(
          LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        )
        .leftMap(e => new DecoderError(e.getMessage()))
    }
  implicit val decoder: RowDecoder[GoodreadsCSVRow] = deriveRowDecoder
}
