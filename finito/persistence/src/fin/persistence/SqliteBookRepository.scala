package fin.persistence

import java.time.LocalDate

import cats.data.NonEmptyList
import cats.implicits._
import doobie.Fragments._
import doobie._
import doobie.implicits._

import fin.Types._

object SqliteBookRepository extends BookRepository[ConnectionIO] {

  import BookFragments._

  override def retrieveBook(isbn: String): ConnectionIO[Option[UserBook]] =
    BookFragments
      .retrieveBook(isbn)
      .query[BookRow]
      .option
      .nested
      .map(_.toBook)
      .value

  override def retrieveMultipleBooks(
      isbns: List[String]
  ): ConnectionIO[List[UserBook]] =
    NonEmptyList.fromList(isbns).fold(List.empty[UserBook].pure[ConnectionIO]) {
      isbnNel =>
        BookFragments
          .retrieveMultipleBooks(isbnNel)
          .query[BookRow]
          .to[List]
          .nested
          .map(_.toBook)
          .value
    }

  override def createBook(
      book: BookInput,
      date: LocalDate
  ): ConnectionIO[Unit] =
    BookFragments.insert(book, date).update.run.void

  override def rateBook(book: BookInput, rating: Int): ConnectionIO[Unit] =
    BookFragments.insertRating(book.isbn, rating).update.run.void

  override def startReading(
      book: BookInput,
      date: LocalDate
  ): ConnectionIO[Unit] =
    BookFragments
      .insertCurrentlyReading(book.isbn, date)
      .update
      .run
      .void

  override def finishReading(
      book: BookInput,
      date: LocalDate
  ): ConnectionIO[Unit] =
    for {
      maybeStarted <-
        BookFragments
          .retrieveStartedFromCurrentlyReading(book.isbn)
          .query[LocalDate]
          .option
      _ <- maybeStarted.traverse { _ =>
        BookFragments.deleteCurrentlyReading(book.isbn).update.run
      }
      _ <- BookFragments.insertRead(book.isbn, maybeStarted, date).update.run
    } yield ()

  override def deleteBookData(isbn: String): ConnectionIO[Unit] =
    for {
      _ <- BookFragments.deleteCurrentlyReading(isbn).update.run
      _ <- BookFragments.deleteRead(isbn).update.run
      _ <- BookFragments.deleteRated(isbn).update.run
    } yield ()
}

object BookFragments {

  implicit val localDatePut: Put[LocalDate] =
    Put[String].contramap(_.toString)

  implicit val localDateGet: Get[LocalDate] =
    Get[String].map(LocalDate.parse(_))

  val lastRead: Fragment =
    fr"""
       |SELECT isbn, MAX(finished) AS finished
       |FROM read_books
       |GROUP BY isbn""".stripMargin

  def retrieveBook(isbn: String): Fragment =
    selectBook ++ fr"WHERE b.isbn=$isbn"

  def retrieveMultipleBooks(isbns: NonEmptyList[String]): Fragment =
    selectBook ++ fr"WHERE" ++ in(fr"b.isbn", isbns)

  def retrieveByIsbn(isbn: String): Fragment =
    fr"SELECT * from books WHERE isbn=$isbn"

  def checkIsbn(isbn: String): Fragment =
    fr"SELECT isbn from books WHERE isbn=$isbn"

  def insert(book: BookInput, date: LocalDate): Fragment =
    fr"""
       |INSERT INTO books VALUES (
       |  ${book.isbn},
       |  ${book.title},
       |  ${book.authors.mkString(",")},
       |  ${book.description},
       |  ${book.thumbnailUri},
       |  $date
       |)""".stripMargin

  def addToCollection(collectionName: String, isbn: String): Fragment =
    fr"INSERT INTO collection_books VALUES ($collectionName, $isbn)"

  def insertCurrentlyReading(isbn: String, start: LocalDate): Fragment =
    fr"""
       |INSERT INTO currently_reading_books (isbn, started)
       |VALUES ($isbn, $start)""".stripMargin

  def retrieveStartedFromCurrentlyReading(isbn: String): Fragment =
    fr"""
       |SELECT started FROM currently_reading_books
       |WHERE isbn=$isbn""".stripMargin

  def deleteCurrentlyReading(isbn: String): Fragment =
    fr"""
       |DELETE FROM currently_reading_books
       |WHERE isbn = $isbn""".stripMargin

  def insertRead(
      isbn: String,
      maybeStarted: Option[LocalDate],
      finished: LocalDate
  ): Fragment =
    fr"""
       |INSERT OR IGNORE INTO read_books (isbn, started, finished)
       |VALUES ($isbn, $maybeStarted, $finished)""".stripMargin

  def insertRating(isbn: String, rating: Int): Fragment =
    fr"""
       |INSERT INTO rated_books (isbn, rating)
       |VALUES ($isbn, $rating)
       |ON CONFLICT(isbn)
       |DO UPDATE SET rating=excluded.rating""".stripMargin

  def deleteRead(isbn: String): Fragment =
    fr"""
       |DELETE FROM read_books
       |WHERE isbn = $isbn""".stripMargin

  def deleteRated(isbn: String): Fragment =
    fr"""
       |DELETE FROM rated_books
       |WHERE isbn = $isbn""".stripMargin

  private def selectBook: Fragment =
    fr"""
       |SELECT 
       |  title,
       |  authors,
       |  description,
       |  b.isbn,
       |  thumbnail_uri,
       |  added,
       |  cr.started,
       |  lr.finished,
       |  r.rating
       |FROM books b
       |LEFT JOIN currently_reading_books cr ON b.isbn = cr.isbn
       |LEFT JOIN (${lastRead}) lr ON b.isbn = lr.isbn
       |LEFT JOIN rated_books r ON b.isbn = r.isbn""".stripMargin
}

final case class BookRow(
    title: String,
    authors: String,
    description: String,
    isbn: String,
    thumbnailUri: String,
    maybeAdded: Option[LocalDate],
    maybeStarted: Option[LocalDate],
    maybeFinished: Option[LocalDate],
    maybeRating: Option[Int]
) {
  def toBook: UserBook =
    UserBook(
      title,
      authors.split(",").toList,
      description,
      isbn,
      thumbnailUri,
      maybeRating,
      maybeStarted,
      maybeFinished
    )
}
