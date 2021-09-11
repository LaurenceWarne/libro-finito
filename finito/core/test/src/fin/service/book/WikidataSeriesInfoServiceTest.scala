package fin.service.book

import cats.effect._
import cats.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.Types._
import org.http4s.client.Client
import org.http4s.Response
import fin.service.search.BookInfoService

object WikidataSeriesInfoServiceTest extends SimpleIOSuite {

  implicit def unsafeLogger: Logger[IO] = Slf4jLogger.getLogger

  def mockedClient(response: String): Client[IO] =
    Client.apply[IO](_ =>
      Resource.pure[IO, Response[IO]](
        Response[IO](body = fs2.Stream.emits(response.getBytes("UTF-8")))
      )
    )

  val emptyBook = UserBook("", List.empty, "", "", "", None, None, None, None)

  test("series returns correct response") {
    val (title1, title2, title3) =
      ("Neuromancer", "Count Zero", "Mona Lisa Overdrive")
    val author = "William Gibson"
    val client = mockedClient(Mocks.trilogy(title1, title2, title3))
    val books = List(title1, title2, title3).map(t =>
      emptyBook.copy(title = t, authors = List(author))
    )
    val bookInfoService = new BookInfoServiceUsingTitles(books)
    val service =
      new WikidataSeriesInfoService(client, bookInfoService)
    for {
      response <-
        service
          .series(
            QueriesSeriesArgs(BookInput(title1, List(author), "", "", ""))
          )
    } yield expect(response.toSet === books.toSet)
  }
}

class BookInfoServiceUsingTitles(books: List[UserBook])
    extends BookInfoService[IO] {

  override def search(booksArgs: QueriesBooksArgs): IO[List[UserBook]] =
    books.filter(b => booksArgs.titleKeywords.exists(_ === b.title)).pure[IO]

  override def fromIsbn(bookArgs: QueriesBookArgs): IO[List[UserBook]] = ???
}

object Mocks {
  def trilogy(title1: String, title2: String, title3: String) = show"""{
  "head" : {
    "vars" : [ "book", "seriesBookLabel", "ordinal" ]
  },
  "results" : {
    "bindings" : [ {
      "book" : {
        "type" : "uri",
        "value" : "http://www.wikidata.org/entity/Q662029"
      },
      "seriesBookLabel" : {
        "xml:lang" : "en",
        "type" : "literal",
        "value" : "$title1"
      },
      "ordinal" : {
        "type" : "literal",
        "value" : "1"
      }
    }, {
      "book" : {
        "type" : "uri",
        "value" : "http://www.wikidata.org/entity/Q662029"
      },
      "seriesBookLabel" : {
        "xml:lang" : "en",
        "type" : "literal",
        "value" : "$title3"
      },
      "ordinal" : {
        "type" : "literal",
        "value" : "3"
      }
    }, {
      "book" : {
        "type" : "uri",
        "value" : "http://www.wikidata.org/entity/Q662029"
      },
      "seriesBookLabel" : {
        "xml:lang" : "en",
        "type" : "literal",
        "value" : "$title2"
      },
      "ordinal" : {
        "type" : "literal",
        "value" : "2"
      }
    } ]
  }
}"""
}
