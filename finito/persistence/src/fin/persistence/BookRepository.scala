package fin.persistence

import java.time.LocalDate

import fin.Types._

trait BookRepository[F[_]] {
  def retrieveBook(isbn: String): F[Option[UserBook]]
  def retrieveMultipleBooks(isbns: List[String]): F[List[UserBook]]
  def createBook(book: BookInput, date: LocalDate): F[Unit]
  def createBooks(books: List[UserBook]): F[Unit]
  def rateBook(book: BookInput, rating: Int): F[Unit]
  def addBookReview(book: BookInput, review: String): F[Unit]
  def startReading(book: BookInput, date: LocalDate): F[Unit]
  def finishReading(book: BookInput, date: LocalDate): F[Unit]
  def deleteBookData(isbn: String): F[Unit]
  def retrieveBooksInside(from: LocalDate, to: LocalDate): F[List[UserBook]]
}
