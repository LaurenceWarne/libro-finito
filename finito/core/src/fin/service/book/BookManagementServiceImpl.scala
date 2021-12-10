package fin.service.book

import java.time.LocalDate

import cats.effect._
import cats.implicits._
import cats.{MonadThrow, ~>}

import fin.BookConversions._
import fin.Types._
import fin._
import fin.persistence.{BookRepository, Dates}

class BookManagementServiceImpl[F[_]: MonadThrow, G[_]: MonadThrow] private (
    bookRepo: BookRepository[G],
    clock: Clock[F],
    transact: G ~> F
) extends BookManagementService[F] {

  override def createBook(args: MutationsCreateBookArgs): F[UserBook] = {
    val transaction: LocalDate => G[UserBook] = date =>
      for {
        maybeBook <- bookRepo.retrieveBook(args.book.isbn)
        _ <- maybeBook.fold(bookRepo.createBook(args.book, date)) { _ =>
          MonadThrow[G].raiseError(BookAlreadyExistsError(args.book))
        }
      } yield toUserBook(args.book)
    Dates
      .currentDate(clock)
      .flatMap(date => transact(transaction(date)))
  }

  override def rateBook(args: MutationsRateBookArgs): F[UserBook] = {
    val transaction: LocalDate => G[UserBook] = date =>
      for {
        book <- createIfNotExists(args.book, date)
        _    <- bookRepo.rateBook(args.book, args.rating)
      } yield book.copy(rating = args.rating.some)
    Dates
      .currentDate(clock)
      .flatMap(date => transact(transaction(date)))
  }

  override def startReading(args: MutationsStartReadingArgs): F[UserBook] = {
    val transaction: (LocalDate, LocalDate) => G[UserBook] =
      (currentDate, startDate) =>
        for {
          book <- createIfNotExists(args.book, currentDate)
          _ <- MonadThrow[G].raiseWhen(book.startedReading.nonEmpty) {
            BookAlreadyBeingReadError(args.book)
          }
          _ <- bookRepo.startReading(args.book, startDate)
        } yield book.copy(startedReading = startDate.some)
    Dates
      .currentDate(clock)
      .flatMap(date => transact(transaction(date, args.date.getOrElse(date))))
  }

  override def finishReading(args: MutationsFinishReadingArgs): F[UserBook] = {
    val transaction: (LocalDate, LocalDate) => G[UserBook] =
      (currentDate, finishDate) =>
        for {
          book <- createIfNotExists(args.book, currentDate)
          _    <- bookRepo.finishReading(args.book, finishDate)
        } yield book.copy(startedReading = None, lastRead = finishDate.some)
    Dates
      .currentDate(clock)
      .flatMap(date => transact(transaction(date, args.date.getOrElse(date))))
  }

  override def deleteBookData(args: MutationsDeleteBookDataArgs): F[Unit] =
    transact(bookRepo.deleteBookData(args.isbn)).void

  private def createIfNotExists(
      book: BookInput,
      date: LocalDate
  ): G[UserBook] =
    for {
      maybeBook <- bookRepo.retrieveBook(book.isbn)
      _ <- MonadThrow[G].whenA(maybeBook.isEmpty)(
        bookRepo.createBook(book, date)
      )
    } yield maybeBook.getOrElse(toUserBook(book, dateAdded = date.some))
}

object BookManagementServiceImpl {
  def apply[F[_]: MonadThrow, G[_]: MonadThrow](
      bookRepo: BookRepository[G],
      clock: Clock[F],
      transact: G ~> F
  ) = new BookManagementServiceImpl[F, G](bookRepo, clock, transact)
}
