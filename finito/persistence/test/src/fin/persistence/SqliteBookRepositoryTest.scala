package fin.persistence

import java.time.LocalDate

import cats.implicits._
import cats.kernel.Eq
import doobie._
import doobie.implicits._

import fin.BookConversions._
import fin.fixtures
import fin.implicits._

object SqliteBookRepositoryTest extends SqliteSuite {

  import BookFragments._

  implicit val dateEq: Eq[LocalDate] = Eq.fromUniversalEquals

  val repo = SqliteBookRepository

  testDoobie("createBook creates book") {
    for {
      _         <- repo.createBook(fixtures.bookInput, fixtures.date)
      maybeBook <- repo.retrieveBook(fixtures.bookInput.isbn)
    } yield expect(
      maybeBook.exists(
        _ === toUserBook(fixtures.bookInput, dateAdded = fixtures.date.some)
      )
    )
  }

  testDoobie("rateBook rates book") {
    val bookToRate = fixtures.bookInput.copy(isbn = "rateme")
    val rating     = 5
    for {
      _           <- repo.createBook(bookToRate, fixtures.date)
      _           <- repo.rateBook(bookToRate, rating)
      maybeRating <- retrieveRating(bookToRate.isbn)
    } yield expect(maybeRating.exists(_ === rating))
  }

  testDoobie("startReading starts book reading") {
    val bookToRead = fixtures.bookInput.copy(isbn = "reading")
    for {
      _          <- repo.createBook(bookToRead, fixtures.date)
      _          <- repo.startReading(bookToRead, fixtures.date)
      maybeEpoch <- retrieveReading(bookToRead.isbn)
    } yield expect(maybeEpoch.exists(_ === fixtures.date))
  }

  testDoobie("finishReading finishes book reading") {
    val finishedDate = LocalDate.parse("2020-03-24")
    val bookToFinish = fixtures.bookInput.copy(isbn = "finished")
    for {
      _          <- repo.createBook(bookToFinish, fixtures.date)
      _          <- repo.startReading(bookToFinish, fixtures.date)
      _          <- repo.finishReading(bookToFinish, finishedDate)
      maybeDates <- retrieveFinished(bookToFinish.isbn)
      (maybeStarted, maybeFinished) = maybeDates.unzip
    } yield expect(maybeStarted.flatten.exists(_ === fixtures.date)) and expect(
      maybeFinished.exists(_ === finishedDate)
    )
  }

  testDoobie("finishReading deletes row from currently_reading table") {
    val finishedDate = LocalDate.parse("2020-03-24")
    val bookToFinish = fixtures.bookInput.copy(isbn = "finished-and-delete")
    for {
      _         <- repo.createBook(bookToFinish, fixtures.date)
      _         <- repo.startReading(bookToFinish, fixtures.date)
      _         <- repo.finishReading(bookToFinish, finishedDate)
      maybeDate <- retrieveReading(bookToFinish.isbn)
    } yield expect(maybeDate.isEmpty)
  }

  testDoobie(
    "finishReading sets started to null when no existing currently reading"
  ) {
    val finishedDate = LocalDate.parse("2020-03-24")
    val bookToFinish = fixtures.bookInput.copy(isbn = "finished-no-reading")
    for {
      _         <- repo.createBook(bookToFinish, fixtures.date)
      _         <- repo.finishReading(bookToFinish, finishedDate)
      maybeRead <- retrieveFinished(bookToFinish.isbn).map(_._1F)
      // maybeRead should be Some(None) => ie found a date but was null
    } yield expect(maybeRead.exists(_.isEmpty))
  }

  testDoobie(
    "finishReading ignores duplicate entries"
  ) {
    val finishedDate = LocalDate.parse("2020-03-24")
    val bookToFinish = fixtures.bookInput.copy(isbn = "finished-duplicated")
    for {
      _        <- repo.createBook(bookToFinish, fixtures.date)
      _        <- repo.finishReading(bookToFinish, finishedDate)
      response <- repo.finishReading(bookToFinish, finishedDate).attempt
    } yield expect(response.isRight)
  }

  testDoobie("retrieveBook retrieves all parts of book") {
    val bookToUse          = fixtures.bookInput.copy(isbn = "megabook")
    val rating             = 3
    val startedReadingDate = LocalDate.parse("2020-03-28")
    for {
      _         <- repo.createBook(bookToUse, fixtures.date)
      _         <- repo.rateBook(bookToUse, rating)
      _         <- repo.finishReading(bookToUse, fixtures.date)
      _         <- repo.startReading(bookToUse, startedReadingDate)
      maybeBook <- repo.retrieveBook(bookToUse.isbn)
    } yield expect(
      maybeBook.exists(
        _ === toUserBook(
          bookToUse,
          dateAdded = fixtures.date.some,
          rating = rating.some,
          startedReading = startedReadingDate.some,
          lastRead = fixtures.date.some
        )
      )
    )
  }

  testDoobie("deleteBookData deletes all book data") {
    val bookToUse          = fixtures.bookInput.copy(isbn = "book to delete data from")
    val startedReadingDate = LocalDate.parse("2020-03-28")
    for {
      _         <- repo.createBook(bookToUse, fixtures.date)
      _         <- repo.rateBook(bookToUse, 3)
      _         <- repo.finishReading(bookToUse, fixtures.date)
      _         <- repo.startReading(bookToUse, startedReadingDate)
      _         <- repo.deleteBookData(bookToUse.isbn)
      maybeBook <- repo.retrieveBook(bookToUse.isbn)
    } yield expect(
      maybeBook.exists(
        _ === toUserBook(bookToUse, dateAdded = fixtures.date.some)
      )
    )
  }

  testDoobie("retrieveMultipleBooks retrieves all matching books") {
    val (isbn1, isbn2, isbn3) = ("book1", "book2", "book3")
    val isbns                 = List(isbn1, isbn2, isbn3)
    val book1                 = fixtures.bookInput.copy(isbn = isbn1)
    val book2                 = fixtures.bookInput.copy(isbn = isbn2)
    val book3                 = fixtures.bookInput.copy(isbn = isbn3)
    for {
      _     <- repo.createBook(book1, fixtures.date)
      _     <- repo.createBook(book2, fixtures.date)
      _     <- repo.createBook(book3, fixtures.date)
      books <- repo.retrieveMultipleBooks(isbns)
    } yield expect(books.size == 3) and expect(
      books.contains(toUserBook(book1, dateAdded = fixtures.date.some))
    ) and expect(
      books.contains(toUserBook(book2, dateAdded = fixtures.date.some))
    ) and expect(
      books.contains(toUserBook(book3, dateAdded = fixtures.date.some))
    )
  }

  testDoobie("retrieveBooksInside retrieves books within interval") {
    val date1 = LocalDate.parse("1920-03-20")
    val date2 = LocalDate.parse("1920-05-13")
    val book1 = fixtures.bookInput.copy(isbn = "old book 1")
    val book2 = fixtures.bookInput.copy(isbn = "old book 2")
    for {
      _ <- repo.createBook(book1, date1)
      _ <- repo.createBook(book2, date2)
      books <- repo.retrieveBooksInside(
        LocalDate.parse("1920-01-01"),
        LocalDate.parse("1921-01-01")
      )
    } yield expect(
      books.sameElements(
        List(
          toUserBook(book1, dateAdded = date1.some),
          toUserBook(book2, dateAdded = date2.some)
        )
      )
    )
  }

  testDoobie("retrieveBooksInside returns nothing for empty interval") {
    for {
      books <- repo.retrieveBooksInside(
        LocalDate.parse("1820-01-01"),
        LocalDate.parse("1821-01-01")
      )
    } yield expect(books.isEmpty)
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
