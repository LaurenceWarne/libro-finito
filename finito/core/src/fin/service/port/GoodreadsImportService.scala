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

import fin.BookAlreadyInCollectionError
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
    bookManagementService: BookManagementService[F],
    specialBookManagementService: BookManagementService[F]
) extends ApplicationImportService[F] {

  import GoodreadsImportService._
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
      // Books stuff
      _ <- Logger[F].debug(
        show"Received ${content.length} chars worth of content"
      )
      rows      <- Async[F].fromEither(result)
      userBooks <- createBooks(rows, langRestrict)
      _         <- markBooks(userBooks)

      // Collections stuff
      rawBookShelfMap = rows
        .map(row => row.sanitizedIsbn -> row.bookshelves)
        .toMap
      existingCollections <- collectionService.collections.map { ls =>
        ls.map(_.name).toSet
      }
      bookShelfMap = rawBookShelfMap.map { case (isbn, shelves) =>
        isbn -> {
          val filtered = shelves.filterNot(SpecialGoodreadsShelves.contains)
          // Match e.g. 'wishlist' to 'Wishlist'
          filtered.map { shelf =>
            existingCollections
              .find(_.toLowerCase === shelf.toLowerCase)
              .getOrElse(shelf)
          }
        }
      }
      inputBookshelves = bookShelfMap.values.flatten.toSet
      collectionsToCreate = inputBookshelves.filterNot { shelf =>
        existingCollections.contains(shelf) ||
        SpecialGoodreadsShelves.contains(shelf)
      }
      _ <- Logger[F].info(
        show"Creating collections ${collectionsToCreate.toList}"
      )
      _ <- collectionService.createCollections(collectionsToCreate.toSet)

      _ <- userBooks
        .flatMap { b =>
          bookShelfMap.getOrElse(b.isbn, Set.empty).toList.tupleLeft(b)
        }
        .traverse { case (book, shelf) =>
          Logger[F].info(s"Adding ${book.title} to ${shelf}") *>
            collectionService
              .addBookToCollection(
                MutationAddBookArgs(Some(shelf), book.toBookInput)
              )
              .void
              .recover { case BookAlreadyInCollectionError(_, _) => () }
        }
    } yield userBooks.length
  }

  private def createBooks(
      rows: List[GoodreadsCSVRow],
      langRestrict: Option[String]
  ): F[List[UserBook]] = {
    for {
      userBooks <- rows
        .map { b =>
          b.title match {
            case s"$title ($_ #$_)" => b.copy(title = title)
            case _                  => b
          }
        }
        .parTraverseN(parallelism) { row =>
          bookInfoService
            .search(
              QueryBooksArgs(
                titleKeywords = Some(row.title),
                authorKeywords = Some(row.author),
                maxResults = Some(5),
                langRestrict = langRestrict
              )
            )
            .flatMap { books =>
              books.headOption.fold {
                Logger[F]
                  .error(
                    show"Failed obtaining extra information for: ${row.title}"
                  )
                  .as(row.toUserBook("", ""))
              } { book =>
                Logger[F]
                  .info(
                    show"Succeeded obtaining extra information for: ${row.title}"
                  )
                  .as(row.toUserBook(book.description, book.thumbnailUri))
              }
            } <* timer.sleep(500.millis)
        }
      _ <- bookManagementService.createBooks(userBooks)
    } yield userBooks
  }

  private def markBooks(books: List[UserBook]): F[Unit] = {
    for {
      _ <- books.map(b => (b, b.lastRead)).traverseCollect {
        case (b, Some(date)) =>
          specialBookManagementService.finishReading(
            MutationFinishReadingArgs(b.toBookInput, Some(date))
          ) *> Logger[F]
            .info(show"Marked ${b.title} as finished on ${date.toString}")
      }
      _ <- books.map(b => (b, b.startedReading)).traverseCollect {
        case (b, Some(date)) =>
          specialBookManagementService.startReading(
            MutationStartReadingArgs(b.toBookInput, Some(date))
          ) *> Logger[F]
            .info(show"Marked ${b.title} as started on ${date.toString}")
      }
      _ <- books.map(b => (b, b.rating)).traverseCollect {
        case (b, Some(rating)) =>
          specialBookManagementService.rateBook(
            MutationRateBookArgs(b.toBookInput, rating)
          ) *> Logger[F].info(show"Gave ${b.title} a rating of $rating")
      }
    } yield ()
  }
}

object GoodreadsImportService {

  val GoodreadsCurrentlyReadingShelf = "currently-reading"
  private val SpecialGoodreadsShelves =
    Set(GoodreadsCurrentlyReadingShelf, "read", "to-read", "favorites")

  def apply[F[_]: Async: Logger](
      bookInfoService: BookInfoService[F],
      collectionService: CollectionService[F],
      bookManagementService: BookManagementService[F],
      specialBookManagementService: BookManagementService[F]
  ): GoodreadsImportService[F] =
    new GoodreadsImportService(
      bookInfoService,
      collectionService,
      bookManagementService,
      specialBookManagementService
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
  import GoodreadsImportService.GoodreadsCurrentlyReadingShelf

  def sanitizedIsbn = isbn.replace("\"", "")

  def toUserBook(description: String, thumbnailUri: String): UserBook =
    UserBook(
      title = title,
      authors = author :: additionalAuthors.fold(List.empty[String])(
        _.split(", ").toList
      ),
      description = description,
      isbn = sanitizedIsbn,
      thumbnailUri = thumbnailUri,
      dateAdded = Some(dateAdded),
      rating = rating,
      // Goodreads doesn't export the date a user started reading a book, so we just use the date added
      startedReading = Option.when(
        bookshelves.contains(GoodreadsCurrentlyReadingShelf)
      )(dateAdded),
      lastRead = dateRead,
      review = myReview
    )

  def bookshelves: Set[String] =
    (bookshelvesStr.split(", ").toSet + exclusiveShelf)
      .map(_.strip())
      .filter(_.nonEmpty)
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
