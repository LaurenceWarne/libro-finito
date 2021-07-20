package fin.service

import java.sql.Date
import java.time.Instant

import scala.concurrent.duration.MILLISECONDS

import cats.effect.Clock
import cats.implicits._
import cats.{Monad, MonadError}

import fin.BookConversions._
import fin.Types._
import fin.implicits._
import fin.persistence.BookRepository
import fin.persistence.DateConversions._

class BookManagementServiceImpl[F[_]] private (
    bookRepo: BookRepository[F],
    clock: Clock[F]
)(implicit ev: MonadError[F, Throwable])
    extends BookManagementService[F] {

  override def createBook(args: MutationsCreateBookArgs): F[UserBook] =
    for {
      maybeBook <- bookRepo.retrieveBook(args.book.isbn)
      _ <- maybeBook.fold(createBook(args.book)) { _ =>
        MonadError[F, Throwable].raiseError(BookAlreadyExistsError(args.book))
      }
    } yield toUserBook(args.book)

  override def rateBook(args: MutationsRateBookArgs): F[UserBook] =
    for {
      _ <- createIfNotExists(args.book)
      _ <- bookRepo.rateBook(args.book, args.rating)
    } yield toUserBook(args.book, rating = args.rating.some)

  override def startReading(args: MutationsStartReadingArgs): F[UserBook] =
    for {
      book <- createIfNotExists(args.book)
      _ <- Monad[F].whenA(book.startedReading.nonEmpty) {
        MonadError[F, Throwable].raiseError(
          BookAlreadyBeingReadError(args.book)
        )
      }
      date <- args.date.fold(getDate)(instantToDate(_).pure[F])
      _    <- bookRepo.startReading(args.book, date)
    } yield toUserBook(args.book, startedReading = dateToInstant(date).some)

  override def finishReading(args: MutationsFinishReadingArgs): F[UserBook] =
    for {
      _    <- createIfNotExists(args.book)
      date <- args.date.fold(getDate)(instantToDate(_).pure[F])
      _    <- bookRepo.finishReading(args.book, date)
    } yield toUserBook(args.book, lastRead = dateToInstant(date).some)

  private def createIfNotExists(book: BookInput): F[UserBook] =
    for {
      maybeBook <- bookRepo.retrieveBook(book.isbn)
      _         <- Monad[F].whenA(maybeBook.isEmpty)(createBook(book))
    } yield maybeBook.getOrElse(toUserBook(book))

  private def createBook(book: BookInput): F[Unit] =
    for {
      date <- getDate
      _    <- bookRepo.createBook(book, date)
    } yield ()

  private def getDate: F[Date] =
    clock
      .realTime(MILLISECONDS)
      .map(t => instantToDate(Instant.ofEpochMilli(t)))
}

object BookManagementServiceImpl {
  def apply[F[_]](bookRepo: BookRepository[F], clock: Clock[F])(implicit
      ev: MonadError[F, Throwable]
  ) =
    new BookManagementServiceImpl(bookRepo, clock)
}

case class BookAlreadyBeingReadError(book: BookInput) extends Throwable {
  override def getMessage = show"The book $book is already being read!"
}

case class BookAlreadyExistsError(book: BookInput) extends Throwable {
  override def getMessage =
    show"A book with isbn ${book.isbn} already exists: $book!"
}
