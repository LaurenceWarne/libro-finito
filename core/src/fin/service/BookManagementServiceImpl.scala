package fin.service

import java.sql.Date
import java.time.LocalDate

import scala.concurrent.duration.DAYS

import cats.Monad
import cats.effect.Clock
import cats.implicits._

import fin.Types._
import fin.persistence.BookRepository

class BookManagementServiceImpl[F[_]: Monad] private (
    bookRepo: BookRepository[F],
    clock: Clock[F]
) extends BookManagementService[F] {

  override def createBook(args: MutationsCreateBookArgs): F[Unit] =
    createBook(args.book)

  override def rateBook(args: MutationsRateBookArgs): F[Book] =
    for {
      _ <- createIfNotExists(args.book)
      _ <- bookRepo.rateBook(args.book, args.rating)
    } yield args.book

  override def startReading(args: MutationsStartReadingArgs): F[Book] =
    for {
      _    <- createIfNotExists(args.book)
      date <- getDate
      _    <- bookRepo.startReading(args.book, date)
    } yield args.book

  override def finishReading(args: MutationsFinishReadingArgs): F[Book] =
    for {
      _    <- createIfNotExists(args.book)
      date <- getDate
      _    <- bookRepo.finishReading(args.book, date)
    } yield args.book

  private def createIfNotExists(book: Book): F[Unit] =
    for {
      maybeBook <- bookRepo.retrieveBook(book.isbn)
      _         <- Monad[F].whenA(maybeBook.isEmpty)(createBook(book))
    } yield ()

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
  def apply[F[_]: Monad](bookRepo: BookRepository[F], clock: Clock[F]) =
    new BookManagementServiceImpl(bookRepo, clock)
}
