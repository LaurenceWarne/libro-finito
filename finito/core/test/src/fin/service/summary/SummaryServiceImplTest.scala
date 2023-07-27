package fin.service.summary

import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.Types._
import fin.fixtures
import fin.persistence.BookRepository
import fin.service.book.InMemoryBookRepository

object SummaryServiceImplTest extends IOSuite {

  val imgUri =
    "https://user-images.githubusercontent.com/17688577/144673930-add9233d-9308-4972-8043-2f519d808874.png"
  val (imgWidth, imgHeight) = (128, 195)

  implicit def unsafeLogger: Logger[IO] = Slf4jLogger.getLogger

  override type Res = (BookRepository[IO], SummaryService[IO])
  override def sharedResource
      : Resource[IO, (BookRepository[IO], SummaryService[IO])] =
    Resource.eval(Ref.of[IO, List[UserBook]](List.empty).map { ref =>
      val repo           = new InMemoryBookRepository(ref)
      val montageService = BufferedImageMontageService[IO]
      (
        repo,
        SummaryServiceImpl[IO, IO](
          repo,
          montageService,
          fixtures.clock,
          FunctionK.id[IO]
        )
      )
    })

  test("summary has correct number of books added") {
    case (repo, summaryService) =>
      val noImages = 16
      for {
        _ <- (1 to noImages).toList.traverse { idx =>
          repo.createBook(
            fixtures.bookInput.copy(
              title = show"book-$idx",
              isbn = show"isbn-$idx",
              thumbnailUri = imgUri
            ),
            fixtures.date.plusDays(idx.toLong)
          )
        }
        summary <- summaryService.summary(
          QueriesSummaryArgs(
            fixtures.date.some,
            fixtures.date.plusYears(1).some,
            None,
            true
          )
        )
      } yield expect(summary.added == noImages)
  }
}
