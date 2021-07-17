package fin.persistence

import java.sql.Date

import fin.Types._

trait BookRepository[F[_]] {
  def createBook(book: Book, date: Date): F[Unit]
  def rateBook(book: Book, rating: Int): F[Unit]
  def startReading(book: Book, date: Date): F[Unit]
  def finishReading(book: Book, date: Date): F[Unit]
}
