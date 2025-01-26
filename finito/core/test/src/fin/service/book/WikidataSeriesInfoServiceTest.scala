package fin.service.book

import cats.effect._
import cats.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.Types._
import fin.fixtures

object WikidataSeriesInfoServiceTest extends SimpleIOSuite {

  implicit def unsafeLogger: Logger[IO] = Slf4jLogger.getLogger

  test("series returns correct response") {
    val client =
      fixtures.HTTPClient(
        fixtures.SeriesResponses
          .trilogy(fixtures.title1, fixtures.title2, fixtures.title3)
      )
    val books = List(fixtures.title1, fixtures.title2, fixtures.title3).map(t =>
      fixtures.emptyBook.copy(title = t, authors = List(fixtures.author))
    )
    val bookInfoService = new BookInfoServiceUsingTitles(books)
    val service         = WikidataSeriesInfoService(client, bookInfoService)
    for {
      response <-
        service
          .series(
            QuerySeriesArgs(
              BookInput(fixtures.title1, List(fixtures.author), "", "", "")
            )
          )
    } yield expect(response.toSet === books.toSet)
  }

  test("series skips book when not found by book info service") {
    val client =
      fixtures.HTTPClient(
        fixtures.SeriesResponses
          .trilogy(fixtures.title1, fixtures.title2, fixtures.title3)
      )
    val books = List(fixtures.title1, fixtures.title3).map(t =>
      fixtures.emptyBook.copy(title = t, authors = List(fixtures.author))
    )
    val bookInfoService = new BookInfoServiceUsingTitles(books)
    val service         = WikidataSeriesInfoService(client, bookInfoService)
    for {
      response <-
        service
          .series(
            QuerySeriesArgs(
              BookInput(fixtures.title1, List(fixtures.author), "", "", "")
            )
          )
    } yield expect(response.toSet === books.toSet)
  }

  test("series returns error when ordinal not integral") {
    val client = fixtures.HTTPClient(fixtures.SeriesResponses.badOrdinal)
    val bookInfoService = new BookInfoServiceUsingTitles(List.empty)
    val service         = WikidataSeriesInfoService(client, bookInfoService)
    for {
      response <-
        service
          .series(
            QuerySeriesArgs(BookInput("", List(fixtures.author), "", "", ""))
          )
          .attempt
    } yield expect(response.isLeft)
  }
}
