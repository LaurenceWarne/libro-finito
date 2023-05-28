package fin.service.port

import cats.effect._
import weaver._

import fin.Types
import fin.service._
import fin.service.book._
import fin.service.collection._
import fin.service.port._

object PortTest extends SimpleIOSuite {

  import fin.service.book.WikidataSeriesInfoServiceTest._

  val (title1, title2, title3) =
    ("Neuromancer", "Count Zero", "Mona Lisa Overdrive")
  val author = "William Gibson"
  val client = mockedClient(Mocks.trilogy(title1, title2, title3))
  val books = List(title1, title2, title3).map(t =>
    emptyBook.copy(title = t, authors = List(author))
  )
  val bookInfoService = new BookInfoServiceUsingTitles(books)

  test("foo".ignore) {
    for {
      colRef <- Ref.of[IO, List[Types.Collection]](List.empty)
      repo = new InMemoryCollectionRepository(colRef)
      collection <-
        new GoodreadsImport[IO](None, bookInfoService).importResource(
          "./assets/sample_goodreads_export.csv",
          None
        )
      _ = println(collection)
    } yield success
  }
}
