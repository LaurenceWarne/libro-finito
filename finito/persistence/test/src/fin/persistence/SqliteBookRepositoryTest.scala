package fin.persistence

import java.time.LocalDate

import cats.effect.IO
import cats.implicits._
import cats.kernel.Eq
import doobie.implicits._

import fin.BookConversions._
import fin.Types._
import fin.implicits._

object SqliteBookRepositoryTest extends SqliteSuite {

  import BookFragments._

  implicit val dateEq: Eq[LocalDate] = Eq.fromUniversalEquals
  val repo                           = SqliteBookRepository(xa)
  val date                           = LocalDate.parse("2020-03-20")
  val book =
    BookInput(
      "title",
      List("author"),
      "cool description",
      "???",
      "uri"
    )

  test("createBook creates book") {
    for {
      _         <- repo.createBook(book, date)
      maybeBook <- repo.retrieveBook(book.isbn)
    } yield expect(maybeBook.exists(_ === toUserBook(book)))
  }

  test("rateBook rates book") {
    val bookToRate = book.copy(isbn = "rateme")
    val rating     = 5
    for {
      _           <- repo.createBook(bookToRate, date)
      _           <- repo.rateBook(bookToRate, rating)
      maybeRating <- retrieveRating(bookToRate.isbn)
    } yield expect(maybeRating.exists(_ === rating))
  }

  test("startReading starts book reading") {
    val bookToRead = book.copy(isbn = "reading")
    for {
      _          <- repo.createBook(bookToRead, date)
      _          <- repo.startReading(bookToRead, date)
      maybeEpoch <- retrieveReading(bookToRead.isbn)
    } yield expect(maybeEpoch.exists(_ === date))
  }

  test("finishReading finishes book reading") {
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

  test("finishReading deletes row from currently_reading table") {
    val finishedDate = LocalDate.parse("2020-03-24")
    val bookToFinish = book.copy(isbn = "finished-and-delete")
    for {
      _         <- repo.createBook(bookToFinish, date)
      _         <- repo.startReading(bookToFinish, date)
      _         <- repo.finishReading(bookToFinish, finishedDate)
      maybeDate <- retrieveReading(bookToFinish.isbn)
    } yield expect(maybeDate.isEmpty)
  }

  test(
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

  test(
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

  test("retrieveBook retrieves all parts of book") {
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

  test("deleteBookData deletes all book data") {
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

  private def retrieveRating(isbn: String): IO[Option[Int]] =
    fr"SELECT rating FROM rated_books WHERE isbn=$isbn".stripMargin
      .query[Int]
      .option
      .transact(xa)

  private def retrieveReading(isbn: String): IO[Option[LocalDate]] =
    fr"SELECT started FROM currently_reading_books WHERE isbn=$isbn"
      .query[LocalDate]
      .option
      .transact(xa)

  private def retrieveFinished(
      isbn: String
  ): IO[Option[(Option[LocalDate], LocalDate)]] =
    fr"SELECT started, finished FROM read_books WHERE isbn=$isbn"
      .query[(Option[LocalDate], LocalDate)]
      .option
      .transact(xa)
}
