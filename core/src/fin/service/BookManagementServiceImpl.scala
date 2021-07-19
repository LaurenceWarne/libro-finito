package fin.service

import java.sql.Date
import java.time.LocalDate

import scala.concurrent.duration.DAYS

import cats.effect.Clock
import cats.implicits._
import cats.{Monad, MonadError}

import fin.Types._
import fin.implicits._
import fin.persistence.BookRepository

class BookManagementServiceImpl[F[_]] private (
    bookRepo: BookRepository[F],
    clock: Clock[F]
)(implicit ev: MonadError[F, Throwable])
    extends BookManagementService[F] {

  override def createBook(args: MutationsCreateBookArgs): F[Unit] =
    for {
      maybeBook <- bookRepo.retrieveBook(args.book.isbn)
      _ <- maybeBook.fold(createBook(args.book)) { book =>
        MonadError[F, Throwable].raiseError(BookAlreadyExistsError(book))
      }
    } yield ()

  override def rateBook(args: MutationsRateBookArgs): F[Book] =
    for {
      _ <- createIfNotExists(args.book)
      _ <- bookRepo.rateBook(args.book, args.rating)
    } yield args.book

  override def startReading(args: MutationsStartReadingArgs): F[Book] =
    for {
      book <- createIfNotExists(args.book)
      _ <- Monad[F].whenA(book.userData.startedReading.nonEmpty) {
        MonadError[F, Throwable].raiseError(BookAlreadyBeingReadError(book))
      }
      date <- getDate
      _    <- bookRepo.startReading(args.book, date)
    } yield args.book

  override def finishReading(args: MutationsFinishReadingArgs): F[Book] =
    for {
      _    <- createIfNotExists(args.book)
      date <- getDate
      _    <- bookRepo.finishReading(args.book, date)
    } yield args.book

  private def createIfNotExists(book: Book): F[Book] =
    for {
      maybeBook <- bookRepo.retrieveBook(book.isbn)
      _         <- Monad[F].whenA(maybeBook.isEmpty)(createBook(book))
    } yield maybeBook.getOrElse(book)

  private def createBook(book: Book): F[Unit] =
    for {
      date <- getDate
      _    <- bookRepo.createBook(book, date)
    } yield ()

  private def getDate: F[Date] =
    clock
      .monotonic(DAYS)
      .map(t => Date.valueOf(LocalDate.ofEpochDay(t)))
}

object BookManagementServiceImpl {
  def apply[F[_]](bookRepo: BookRepository[F], clock: Clock[F])(implicit
      ev: MonadError[F, Throwable]
  ) =
    new BookManagementServiceImpl(bookRepo, clock)
}

case class BookAlreadyBeingReadError(book: Book) extends Throwable {
  override def getMessage = show"The book $book is already being read!"
}

case class BookAlreadyExistsError(book: Book) extends Throwable {
  override def getMessage =
    show"A book with isbn ${book.isbn} already exists: $book!"
}
