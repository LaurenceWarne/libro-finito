package fin.service.search

import cats.effect._
import cats.implicits._
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.Types._
import fin._

object GoogleBookInfoServiceTest extends SimpleIOSuite {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  test("search parses title, author and description from json") {
    val title       = "The Casual Vacancy"
    val author      = "J K Rowling"
    val description = "Not Harry Potter"
    val client: Client[IO] =
      fixtures.HTTPClient(
        fixtures.BooksResponses.response(title, author, description)
      )
    val bookAPI: BookInfoService[IO] = GoogleBookInfoService(client)
    for {
      result <-
        bookAPI.search(QueryBooksArgs("non-empty".some, None, None, None))
      maybeBook = result.headOption
    } yield expect(result.length === 1) and
      expect(maybeBook.map(_.title) === title.some) and
      expect(maybeBook.map(_.authors) === List(author).some) and
      expect(maybeBook.map(_.description) === description.some)
  }

  test("search errors with empty strings") {
    val client: Client[IO] =
      fixtures.HTTPClient(fixtures.BooksResponses.response("", "", ""))
    val bookAPI: BookInfoService[IO] = GoogleBookInfoService(client)
    for {
      response <-
        bookAPI
          .search(QueryBooksArgs("".some, "".some, None, None))
          .attempt
    } yield expect(response == NoKeywordsSpecifiedError.asLeft)
  }

  test("search errors with empty optionals") {
    val client: Client[IO] =
      fixtures.HTTPClient(fixtures.BooksResponses.response("", "", ""))
    val bookAPI: BookInfoService[IO] = GoogleBookInfoService(client)
    for {
      response <-
        bookAPI
          .search(QueryBooksArgs(None, None, None, None))
          .attempt
    } yield expect(response == NoKeywordsSpecifiedError.asLeft)
  }

  test("fromIsbn parses title, author and description from json") {
    val isbn = "1568658079"
    val client: Client[IO] =
      fixtures.HTTPClient(fixtures.BooksResponses.isbnResponse(isbn))
    val bookAPI: BookInfoService[IO] = GoogleBookInfoService(client)
    for {
      response <- bookAPI.fromIsbn(QueryBookArgs(isbn, None))
      maybeBook = response.headOption
    } yield expect(response.length === 1) and expect(
      maybeBook.map(_.isbn) === ("978" + isbn).some
    )
  }
}
