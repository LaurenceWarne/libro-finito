package fin.persistence

import java.time.LocalDate

import doobie.ConnectionIO

import fin.Types._

trait BookRepository {
  def retrieveBook(isbn: String): ConnectionIO[Option[UserBook]]
  def retrieveMultipleBooks(isbns: List[String]): ConnectionIO[List[UserBook]]
  def createBook(book: BookInput, date: LocalDate): ConnectionIO[Unit]
  def rateBook(book: BookInput, rating: Int): ConnectionIO[Unit]
  def startReading(book: BookInput, date: LocalDate): ConnectionIO[Unit]
  def finishReading(book: BookInput, date: LocalDate): ConnectionIO[Unit]
  def deleteBookData(isbn: String): ConnectionIO[Unit]
}
