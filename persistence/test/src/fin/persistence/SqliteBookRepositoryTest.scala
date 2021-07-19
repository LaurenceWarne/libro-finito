package fin.persistence

import java.sql.Date
import java.time.{Instant, ZoneId}

import cats.effect.IO
import cats.implicits._
import cats.kernel.Eq
import doobie.implicits._

import fin.BookConversions._
import fin.Types._
import fin.implicits._

object SqliteBookRepositoryTest extends SqliteSuite {

  implicit val dateEq: Eq[Date] = Eq.fromUniversalEquals
  val repo                      = SqliteBookRepository(xa)
  val date                      = Date.valueOf("2020-03-20")
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
    } yield expect(maybeEpoch.exists(toDate(_) === date))
  }

  test("finishReading finishes book reading") {
    val finishedDate = Date.valueOf("2020-03-24")
    val bookToFinish = book.copy(isbn = "finished")
    for {
      _          <- repo.createBook(bookToFinish, date)
      _          <- repo.startReading(bookToFinish, date)
      _          <- repo.finishReading(bookToFinish, finishedDate)
      maybeDates <- retrieveFinished(bookToFinish.isbn)
      (maybeStarted, maybeFinished) = maybeDates.unzip
    } yield expect(maybeStarted.flatten.exists(toDate(_) === date)) and expect(
      maybeFinished.exists(toDate(_) === finishedDate)
    )
  }

  test("finishReading deletes row from currently_reading table") {
    val finishedDate = Date.valueOf("2020-03-24")
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
    val finishedDate = Date.valueOf("2020-03-24")
    val bookToFinish = book.copy(isbn = "finished-no-reading")
    for {
      _         <- repo.createBook(bookToFinish, date)
      _         <- repo.finishReading(bookToFinish, finishedDate)
      maybeRead <- retrieveFinished(bookToFinish.isbn).map(_._1F)
      // maybeRead should be Some(None) => ie found a date but was null
    } yield expect(maybeRead.exists(_.isEmpty))
  }

  test("retrieveBook retrieves all parts of book") {
    val bookToUse          = book.copy(isbn = "megabook")
    val rating             = 3
    val startedReadingDate = Date.valueOf("2020-03-28")
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
          startedReading =
            Instant.ofEpochMilli(startedReadingDate.getTime).some,
          lastRead = Instant.ofEpochMilli(date.getTime).some
        )
      )
    )
  }

  private def retrieveRating(isbn: String): IO[Option[Int]] =
    fr"SELECT rating FROM rated_books WHERE isbn=$isbn".stripMargin
      .query[Int]
      .option
      .transact(xa)

  private def retrieveReading(isbn: String): IO[Option[Long]] =
    fr"SELECT started FROM currently_reading_books WHERE isbn=$isbn"
      .query[Long]
      .option
      .transact(xa)

  private def retrieveFinished(isbn: String): IO[Option[(Option[Long], Long)]] =
    fr"SELECT started, finished FROM read_books WHERE isbn=$isbn"
      .query[(Option[Long], Long)]
      .option
      .transact(xa)

  private def toDate(epoch: Long): Date =
    Date.valueOf(
      Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()
    )
}
