package fin.persistence

import java.sql.Date

import cats.effect.Sync
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.util.transactor.Transactor

import fin.Types._

import BookFragments._

class SqliteBookRepository[F[_]: Sync] private (
    xa: Transactor[F]
) extends BookRepository[F] {

  override def retrieveBook(isbn: String): F[Option[Book]] =
    fr"""
       |SELECT title, authors, description, isbn, thumbnail_uri
       |FROM books WHERE isbn=$isbn""".stripMargin
      .query[BookRow]
      .option
      .transact(xa)
      .nested
      .map(_.toBook)
      .value

  override def createBook(book: Book, date: Date): F[Unit] =
    insert(book, date).update.run.transact(xa).void

  override def rateBook(book: Book, rating: Int): F[Unit] =
    insertRating(book.isbn, rating).update.run.transact(xa).void

  override def startReading(book: Book, date: Date): F[Unit] =
    insertCurrentlyReading(book.isbn, date).update.run.transact(xa).void

  override def finishReading(book: Book, date: Date): F[Unit] = {
    val transaction =
      for {
        maybeStarted <-
          retrieveStartedFromCurrentlyReading(book.isbn)
            .query[Date]
            .option
        _ <- maybeStarted.traverse { _ =>
          deleteCurrentlyReading(book.isbn).update.run
        }
        _ <- insertRead(book.isbn, maybeStarted, date).update.run
      } yield ()
    transaction.transact(xa)
  }
}

object SqliteBookRepository {

  def apply[F[_]: Sync](xa: Transactor[F]) =
    new SqliteBookRepository[F](xa)
}

object BookFragments {

  def retrieveByIsbn(isbn: String): Fragment =
    fr"select * from books WHERE isbn=$isbn"

  def checkIsbn(isbn: String): Fragment =
    fr"select isbn from books WHERE isbn=$isbn"

  def insert(book: Book, date: Date): Fragment =
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
       |INSERT INTO read_books (isbn, started, finished)
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
    thumbnailUri: String
) {
  def toBook: Book =
    Book(
      title,
      authors.split(",").toList,
      description,
      isbn,
      thumbnailUri,
      None,
      None
    )
}
