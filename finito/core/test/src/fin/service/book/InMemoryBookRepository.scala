package fin.service.book

import java.time.LocalDate

import cats.Monad
import cats.effect.concurrent.Ref
import cats.implicits._

import fin.BookConversions._
import fin.Types._
import fin.implicits._
import fin.persistence.BookRepository

class InMemoryBookRepository[F[_]: Monad](booksRef: Ref[F, List[UserBook]])
    extends BookRepository[F] {

  override def createBook(book: BookInput, date: LocalDate): F[Unit] =
    booksRef.getAndUpdate(toUserBook(book) :: _).void

  override def retrieveBook(isbn: String): F[Option[UserBook]] =
    booksRef.get.map(_.find(_.isbn === isbn))

  override def retrieveMultipleBooks(isbns: List[String]): F[List[UserBook]] =
    booksRef.get.map(_.filter(isbns.contains))

  override def rateBook(book: BookInput, rating: Int): F[Unit] = {
    val userBook = toUserBook(book)
    for {
      _ <- booksRef.getAndUpdate(_.map { b =>
        if (b === userBook)
          userBook.copy(rating = rating.some)
        else b
      })
    } yield ()
  }

  override def startReading(book: BookInput, date: LocalDate): F[Unit] = {
    val userBook = toUserBook(book)
    for {
      _ <- booksRef.getAndUpdate(_.map { b =>
        if (b === userBook)
          userBook.copy(startedReading = date.some)
        else b
      })
    } yield ()
  }

  override def finishReading(book: BookInput, date: LocalDate): F[Unit] = {
    val userBook = toUserBook(book)
    for {
      _ <- booksRef.getAndUpdate(_.map { b =>
        if (b === userBook)
          userBook.copy(lastRead = date.some)
        else b
      })
    } yield ()
  }

  override def deleteBookData(isbn: String): F[Unit] =
    booksRef
      .getAndUpdate(_.map { b =>
        if (b.isbn === isbn)
          b.copy(rating = None, startedReading = None, lastRead = None)
        else b
      })
      .void
}
