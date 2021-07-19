package fin.service

import java.sql.Date
import java.time.Instant

import cats.Monad
import cats.effect.concurrent.Ref
import cats.implicits._

import fin.Types._
import fin.implicits._
import fin.persistence.BookRepository

class InMemoryBookRepository[F[_]: Monad](booksRef: Ref[F, List[Book]])
    extends BookRepository[F] {

  override def createBook(book: Book, date: Date): F[Unit] =
    booksRef.getAndUpdate(book :: _).void

  override def retrieveBook(isbn: String): F[Option[Book]] =
    booksRef.get.map(_.find(_.isbn === isbn))

  override def rateBook(book: Book, rating: Int): F[Unit] =
    for {
      _ <- booksRef.getAndUpdate(_.map { b =>
        if (b === book)
          book.copy(userData = book.userData.copy(rating = rating.some))
        else b
      })
    } yield ()

  override def startReading(book: Book, date: Date): F[Unit] =
    for {
      _ <- booksRef.getAndUpdate(_.map { b =>
        if (b === book)
          book.copy(userData =
            book.userData
              .copy(startedReading = Instant.ofEpochMilli(date.getTime).some)
          )
        else b
      })
    } yield ()

  override def finishReading(book: Book, date: Date): F[Unit] =
    for {
      _ <- booksRef.getAndUpdate(_.map { b =>
        if (b === book)
          book.copy(userData =
            book.userData
              .copy(lastRead = Instant.ofEpochMilli(date.getTime).some)
          )
        else b
      })
    } yield ()
}
