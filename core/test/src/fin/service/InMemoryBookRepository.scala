package fin.service

import java.sql.Date

import cats.Functor
import cats.effect.concurrent.Ref
import cats.implicits._

import fin.Types._
import fin.persistence.BookRepository

class InMemoryBookRepository[F[_]: Functor](booksRef: Ref[F, List[Book]])
    extends BookRepository[F] {

  override def createBook(book: Book, date: Date): F[Unit] =
    booksRef.getAndUpdate(book :: _).void

  override def retrieveBook(isbn: String): F[Option[Book]] =
    booksRef.get.map(_.find(_.isbn === isbn))

  override def rateBook(book: Book, rating: Int): F[Unit] = ???

  override def startReading(book: Book, date: Date): F[Unit] = ???

  override def finishReading(book: Book, date: Date): F[Unit] = ???
}
