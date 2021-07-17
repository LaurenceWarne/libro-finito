package fin.persistence

import java.sql.Date

import cats.MonadError
import cats.effect.Sync
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.util.transactor.Transactor

import fin.Types._
import fin.implicits._

import BookFragments._

class SqliteBookRepository[F[_]: Sync] private (
    xa: Transactor[F]
) extends BookRepository[F] {

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
        // TODO allow this if a started date is specified in gql
        started <- MonadError[ConnectionIO, Throwable].fromOption(
          maybeStarted,
          BookMustBeInProgressToBeFinishedError(book)
        )
        _ <- deleteCurrentlyReading(book.isbn).update.run
        _ <- insertRead(book.isbn, started, date).update.run
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

  def insertRead(isbn: String, started: Date, finished: Date): Fragment =
    fr"""
       |INSERT INTO read_books (isbn, started, finished)
       |VALUES ($isbn, $started, $finished)""".stripMargin

  def insertRating(isbn: String, rating: Int): Fragment =
    fr"""
       |INSERT INTO rated_books (isbn, rating)
       |VALUES ($isbn, $rating)
       |ON CONFLICT(isbn)
       |DO UPDATE SET rating=excluded.rating""".stripMargin
}

case class BookMustBeInProgressToBeFinishedError(book: Book) extends Throwable {
  override def getMessage: String =
    show"""
         |In order to finish reading a book it must first be in progress,
         |but $book is not in progress""".stripMargin.replace("\n", ",")
}
