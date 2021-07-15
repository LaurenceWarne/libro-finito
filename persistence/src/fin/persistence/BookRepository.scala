package fin.persistence

import fin.Types._

trait BookRepository[F[_]] {
  def createBook(book: Book): F[Unit]
  def rateBook(book: Book, rating: Int): F[Unit]
  def startReading(book: Book): F[Unit]
  def finishReading(book: Book): F[Unit]
}
