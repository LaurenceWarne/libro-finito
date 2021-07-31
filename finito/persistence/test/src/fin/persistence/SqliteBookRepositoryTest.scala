package fin.persistence

import java.time.LocalDate

import cats.implicits._
import cats.kernel.Eq
import doobie._
import doobie.implicits._

import fin.BookConversions._
import fin.Types._
import fin.implicits._

object SqliteBookRepositoryTest extends SqliteSuite {

  import BookFragments._

  implicit val dateEq: Eq[LocalDate] = Eq.fromUniversalEquals

  val repo = SqliteBookRepository
  val date = LocalDate.parse("2020-03-20")
  val book =
    BookInput(
      "title",
      List("author"),
      "cool description",
      "???",
      "uri"
    )

  testDoobie("createBook creates book") {
    for {
      _         <- repo.createBook(book, date)
      maybeBook <- repo.retrieveBook(book.isbn)
    } yield expect(maybeBook.exists(_ === toUserBook(book)))
  }

  testDoobie("rateBook rates book") {
    val bookToRate = book.copy(isbn = "rateme")
    val rating     = 5
    for {
      _           <- repo.createBook(bookToRate, date)
      _           <- repo.rateBook(bookToRate, rating)
      maybeRating <- retrieveRating(bookToRate.isbn)
    } yield expect(maybeRating.exists(_ === rating))
  }

  testDoobie("startReading starts book reading") {
    val bookToRead = book.copy(isbn = "reading")
    for {
      _          <- repo.createBook(bookToRead, date)
      _          <- repo.startReading(bookToRead, date)
      maybeEpoch <- retrieveReading(bookToRead.isbn)
    } yield expect(maybeEpoch.exists(_ === date))
  }

  testDoobie("finishReading finishes book reading") {
    val finishedDate = LocalDate.parse("2020-03-24")
    val bookToFinish = book.copy(isbn = "finished")
    for {
      _          <- repo.createBook(bookToFinish, date)
      _          <- repo.startReading(bookToFinish, date)
      _          <- repo.finishReading(bookToFinish, finishedDate)
      maybeDates <- retrieveFinished(bookToFinish.isbn)
      (maybeStarted, maybeFinished) = maybeDates.unzip
    } yield expect(maybeStarted.flatten.exists(_ === date)) and expect(
      maybeFinished.exists(_ === finishedDate)
    )
  }

  testDoobie("finishReading deletes row from currently_reading table") {
    val finishedDate = LocalDate.parse("2020-03-24")
    val bookToFinish = book.copy(isbn = "finished-and-delete")
    for {
      _         <- repo.createBook(bookToFinish, date)
      _         <- repo.startReading(bookToFinish, date)
      _         <- repo.finishReading(bookToFinish, finishedDate)
      maybeDate <- retrieveReading(bookToFinish.isbn)
    } yield expect(maybeDate.isEmpty)
  }

  testDoobie(
    "finishReading sets started to null when no existing currently reading"
  ) {
    val finishedDate = LocalDate.parse("2020-03-24")
    val bookToFinish = book.copy(isbn = "finished-no-reading")
    for {
      _         <- repo.createBook(bookToFinish, date)
      _         <- repo.finishReading(bookToFinish, finishedDate)
      maybeRead <- retrieveFinished(bookToFinish.isbn).map(_._1F)
      // maybeRead should be Some(None) => ie found a date but was null
    } yield expect(maybeRead.exists(_.isEmpty))
  }

  testDoobie(
    "finishReading ignores duplicate entries"
  ) {
    val finishedDate = LocalDate.parse("2020-03-24")
    val bookToFinish = book.copy(isbn = "finished-duplicated")
    for {
      _        <- repo.createBook(bookToFinish, date)
      _        <- repo.finishReading(bookToFinish, finishedDate)
      response <- repo.finishReading(bookToFinish, finishedDate).attempt
    } yield expect(response.isRight)
  }

  testDoobie("retrieveBook retrieves all parts of book") {
    val bookToUse          = book.copy(isbn = "megabook")
    val rating             = 3
    val startedReadingDate = LocalDate.parse("2020-03-28")
    for {
      _         <- repo.createBook(bookToUse, date)
      _         <- repo.rateBook(bookToUse, rating)
      _         <- repo.finishReading(bookToUse, date)
      _         <- repo.startReading(bookToUse, startedReadingDate)
      maybeBook <- repo.retrieveBook(bookToUse.isbn)
    } yield expect(
      maybeBook.exists(
        _ === toUserBook(bookToUse).copy(
          rating = rating.some,
          startedReading = startedReadingDate.some,
          lastRead = date.some
        )
      )
    )
  }

  testDoobie("deleteBookData deletes all book data") {
    val bookToUse          = book.copy(isbn = "book to delete data from")
    val startedReadingDate = LocalDate.parse("2020-03-28")
    for {
      _         <- repo.createBook(bookToUse, date)
      _         <- repo.rateBook(bookToUse, 3)
      _         <- repo.finishReading(bookToUse, date)
      _         <- repo.startReading(bookToUse, startedReadingDate)
      _         <- repo.deleteBookData(bookToUse.isbn)
      maybeBook <- repo.retrieveBook(bookToUse.isbn)
    } yield expect(
      maybeBook.exists(
        _ === toUserBook(bookToUse).copy(
          rating = None,
          startedReading = None,
          lastRead = None
        )
      )
    )
  }

  testDoobie("retrieveMultipleBooks retrieves all matching books") {
    val isbns                     = List("book1", "book2", "book3")
    val List(isbn1, isbn2, isbn3) = isbns
    val book1                     = book.copy(isbn = isbn1)
    val book2                     = book.copy(isbn = isbn2)
    val book3                     = book.copy(isbn = isbn3)
    for {
      _     <- repo.createBook(book1, date)
      _     <- repo.createBook(book2, date)
      _     <- repo.createBook(book3, date)
      books <- repo.retrieveMultipleBooks(isbns)
    } yield expect(books.size == 3) and expect(
      books.contains(toUserBook(book1))
    ) and expect(
      books.contains(toUserBook(book2))
    ) and expect(
      books.contains(toUserBook(book3))
    )
  }

  private def retrieveRating(isbn: String): ConnectionIO[Option[Int]] =
    fr"SELECT rating FROM rated_books WHERE isbn=$isbn".stripMargin
      .query[Int]
      .option

  private def retrieveReading(isbn: String): ConnectionIO[Option[LocalDate]] =
    fr"SELECT started FROM currently_reading_books WHERE isbn=$isbn"
      .query[LocalDate]
      .option

  private def retrieveFinished(
      isbn: String
  ): ConnectionIO[Option[(Option[LocalDate], LocalDate)]] =
    fr"SELECT started, finished FROM read_books WHERE isbn=$isbn"
      .query[(Option[LocalDate], LocalDate)]
      .option
}
