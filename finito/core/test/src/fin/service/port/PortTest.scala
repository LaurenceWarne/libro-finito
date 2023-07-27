package fin.service.port

import cats.effect._
import weaver._

import fin.service.book._
import fin.service.collection._
import fin.service.port._
import fin.{Types, fixtures}

object PortTest extends SimpleIOSuite {

  val client = fixtures.HTTPClient(
    fixtures.SeriesResponses
      .trilogy(fixtures.title1, fixtures.title2, fixtures.title3)
  )
  val books = List(fixtures.title1, fixtures.title2, fixtures.title3).map(t =>
    fixtures.emptyBook.copy(title = t, authors = List(fixtures.author))
  )
  val bookInfoService = new BookInfoServiceUsingTitles(books)

  test("foo".ignore) {
    for {
      colRef <- Ref.of[IO, List[Types.Collection]](List.empty)
      repo = new InMemoryCollectionRepository(colRef)
      _ <- new GoodreadsImport[IO](None, bookInfoService).importResource(
        "./assets/sample_goodreads_export.csv",
        None
      )
    } yield success
  }
}
