package fin.service.summary

import java.time.{LocalDate, ZoneId}

import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import weaver._

import fin.Types._
import fin._
import fin.persistence.BookRepository
import fin.service.book.InMemoryBookRepository
import org.http4s.client.Client
import org.http4s.Response
import java.util.UUID

object SummaryServiceImplTest extends IOSuite {

  val constantDate = LocalDate.parse("2021-11-30")
  val testClock = TestClock[IO](
    constantDate
      .atStartOfDay(ZoneId.systemDefault())
      .toEpochSecond * 1000L
  )

  val book =
    BookInput(
      "title",
      List("author"),
      "cool description",
      "???",
      "uri"
    )

  override type Res = (BookRepository[IO], SummaryService[IO])
  override def sharedResource
      : Resource[IO, (BookRepository[IO], SummaryService[IO])] =
    Resource.eval(Ref.of[IO, List[UserBook]](List.empty).map { ref =>
      val repo   = new InMemoryBookRepository(ref)
      val client = mockedClientRandomTitles
      val montageService =
        BufferedImageMontageService[IO](client, MontageSpecification())
      (
        repo,
        SummaryServiceImpl[IO, IO](
          repo,
          montageService,
          testClock,
          FunctionK.id[IO]
        )
      )
    })

  test("summary") {
    case (repo, summaryService) =>
      for {
        _ <- (1 to 16).toList.traverse { idx =>
          repo.createBook(
            book.copy(title = show"book-$idx", isbn = show"isbn-$idx"),
            constantDate.plusDays(idx.toLong)
          )
        }
        summary <- summaryService.summary(
          constantDate.some,
          constantDate.plusYears(1).some
        )
        _ = println(summary)
      } yield success
  }

  val mockedClientRandomTitles: Client[IO] =
    Client.apply[IO](_ =>
      Resource.eval[IO, Response[IO]](
        IO(UUID.randomUUID()).map { uuid =>
          Response[IO](body =
            fs2.Stream.emits(stubResponse(uuid.toString).getBytes("UTF-8"))
          )
        }
      )
    )

  private def stubResponse(title: String) =
    show"""{
  "items": [
    {
      "volumeInfo": {
        "title": "$title",
        "authors": [
          "bar"
        ],
        "description": "a description",
        "industryIdentifiers": [
          {
            "type": "ISBN_13",
            "identifier": "foobar"
          },
          {
            "type": "ISBN_10",
            "identifier": "foobar"
          }
        ],
        "imageLinks": {
          "smallThumbnail": "https://user-images.githubusercontent.com/17688577/144645773-fdaa9482-016d-48e5-a993-5c4fd6f72dec.jpeg",
          "thumbnail": "https://user-images.githubusercontent.com/17688577/144645773-fdaa9482-016d-48e5-a993-5c4fd6f72dec.jpeg"
        }
    }
  ]
}
"""
}
