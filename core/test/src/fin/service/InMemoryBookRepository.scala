package fin.service

import java.sql.Date
import java.time.Instant

import cats.Monad
import cats.effect.concurrent.Ref
import cats.implicits._

import fin.BookConversions._
import fin.Types._
import fin.implicits._
import fin.persistence.BookRepository

class InMemoryBookRepository[F[_]: Monad](booksRef: Ref[F, List[UserBook]])
    extends BookRepository[F] {

  override def createBook(book: BookInput, date: Date): F[Unit] =
    booksRef.getAndUpdate(toUserBook(book) :: _).void

  override def retrieveBook(isbn: String): F[Option[UserBook]] =
    booksRef.get.map(_.find(_.isbn === isbn))

  override def rateBook(book: BookInput, rating: Int): F[Unit] = {
    val userBook = toUserBook(book)
    for {
      _ <- booksRef.getAndUpdate(_.map { b =>
        if (b === userBook) userBook.copy(rating = rating.some)
        else b
      })
    } yield ()
  }

  override def startReading(book: BookInput, date: Date): F[Unit] = {
    val userBook = toUserBook(book)
    for {
      _ <- booksRef.getAndUpdate(_.map { b =>
        if (b === userBook)
          userBook.copy(startedReading =
            Instant.ofEpochMilli(date.getTime).some
          )
        else b
      })
    } yield ()
  }

  override def finishReading(book: BookInput, date: Date): F[Unit] = {
    val userBook = toUserBook(book)
    for {
      _ <- booksRef.getAndUpdate(_.map { b =>
        if (b === userBook)
          userBook.copy(lastRead = Instant.ofEpochMilli(date.getTime).some)
        else b
      })
    } yield ()
  }
}
