package fin.persistence

import java.sql.Date
import java.time.Instant

import cats.effect.Sync
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.util.transactor.Transactor

import fin.Types._

class SqliteBookRepository[F[_]: Sync] private (
    xa: Transactor[F]
) extends BookRepository[F] {

  override def retrieveBook(isbn: String): F[Option[UserBook]] =
    BookFragments
      .retrieveBook(isbn)
      .query[BookRow]
      .option
      .transact(xa)
      .nested
      .map(_.toBook)
      .value

  override def createBook(book: BookInput, date: Date): F[Unit] =
    BookFragments.insert(book, date).update.run.transact(xa).void

  override def rateBook(book: BookInput, rating: Int): F[Unit] =
    BookFragments.insertRating(book.isbn, rating).update.run.transact(xa).void

  override def startReading(book: BookInput, date: Date): F[Unit] =
    BookFragments
      .insertCurrentlyReading(book.isbn, date)
      .update
      .run
      .transact(xa)
      .void

  override def finishReading(book: BookInput, date: Date): F[Unit] = {
    val transaction =
      for {
        maybeStarted <-
          BookFragments
            .retrieveStartedFromCurrentlyReading(book.isbn)
            .query[Date]
            .option
        _ <- maybeStarted.traverse { _ =>
          BookFragments.deleteCurrentlyReading(book.isbn).update.run
        }
        _ <- BookFragments.insertRead(book.isbn, maybeStarted, date).update.run
      } yield ()
    transaction.transact(xa)
  }
}

object SqliteBookRepository {

  def apply[F[_]: Sync](xa: Transactor[F]) =
    new SqliteBookRepository[F](xa)
}

object BookFragments {

  val lastRead: Fragment =
    fr"""
       |SELECT isbn, MAX(finished) AS finished
       |FROM read_books
       |GROUP BY isbn""".stripMargin

  def retrieveBook(isbn: String): Fragment =
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
       |LEFT JOIN rated_books r ON b.isbn = r.isbn
       |WHERE b.isbn=$isbn""".stripMargin

  def retrieveByIsbn(isbn: String): Fragment =
    fr"SELECT * from books WHERE isbn=$isbn"

  def checkIsbn(isbn: String): Fragment =
    fr"SELECT isbn from books WHERE isbn=$isbn"

  def insert(book: BookInput, date: Date): Fragment =
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

  def insertCurrentlyReading(isbn: String, start: Date): Fragment =
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
      maybeStarted: Option[Date],
      finished: Date
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
}

case class BookRow(
    title: String,
    authors: String,
    description: String,
    isbn: String,
    thumbnailUri: String,
    maybeAdded: Option[Long],
    maybeStarted: Option[Long],
    maybeFinished: Option[Long],
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
      maybeStarted.map(Instant.ofEpochMilli(_)),
      maybeFinished.map(Instant.ofEpochMilli(_))
    )
}
