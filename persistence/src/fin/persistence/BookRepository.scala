package fin.persistence

import java.sql.Date

import fin.Types._

trait BookRepository[F[_]] {
  def retrieveBook(isbn: String): F[Option[UserBook]]
  def createBook(book: BookInput, date: Date): F[Unit]
  def rateBook(book: BookInput, rating: Int): F[Unit]
  def startReading(book: BookInput, date: Date): F[Unit]
  def finishReading(book: BookInput, date: Date): F[Unit]
}
