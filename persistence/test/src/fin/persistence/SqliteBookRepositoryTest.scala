package fin.persistence

import java.sql.Date
import java.time.Instant

import cats.effect.IO
import cats.implicits._
import doobie.implicits._
import doobie.util.Read

import fin.Types._

object SqliteBookRepositoryTest extends SqliteSuite {

  implicit val instantRead: Read[Instant] = Read[String].map(Instant.parse(_))
  val repo                                = SqliteBookRepository(xa)
  val date                                = Date.valueOf("2020-03-20")
  val book =
    Book("title", List("author"), "cool description", "???", "uri", None, None)

  test("createBook creates book") {
    for {
      _         <- repo.createBook(book, date)
      maybeBook <- retrieveBook(book.isbn)
    } yield expect(maybeBook.exists(_ == book))
  }

  private def retrieveBook(isbn: String): IO[Option[Book]] =
    fr"""
       |SELECT title, authors, description, isbn, thumbnail_uri
       |FROM books WHERE isbn=$isbn""".stripMargin
      .query[BookRow]
      .option
      .transact(xa)
      .nested
      .map { b =>
        Book(
          b.title,
          b.authors.split(",").toList,
          b.description,
          b.isbn,
          b.thumbnailUri,
          None,
          None
        )
      }
      .value

}

case class BookRow(
    title: String,
    authors: String,
    description: String,
    isbn: String,
    thumbnailUri: String
)
