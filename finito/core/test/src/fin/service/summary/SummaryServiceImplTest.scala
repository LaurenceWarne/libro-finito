package fin.service.summary

import java.io.ByteArrayInputStream
import java.time.{LocalDate, ZoneId}
import java.util.Base64
import javax.imageio.ImageIO

import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import weaver._

import fin.Types._
import fin._
import fin.persistence.BookRepository
import fin.service.book.InMemoryBookRepository

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

  val imgUri =
    "https://user-images.githubusercontent.com/17688577/144673930-add9233d-9308-4972-8043-2f519d808874.png"
  val (imgWidth, imgHeight) = (128, 195)
  val montageSpecification  = MontageSpecification()

  override type Res = (BookRepository[IO], SummaryService[IO])
  override def sharedResource
      : Resource[IO, (BookRepository[IO], SummaryService[IO])] =
    Resource.eval(Ref.of[IO, List[UserBook]](List.empty).map { ref =>
      val repo           = new InMemoryBookRepository(ref)
      val montageService = BufferedImageMontageService[IO](montageSpecification)
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

  test("summary has image of correct height and number of books added") {
    case (repo, summaryService) =>
      val noImages = 16
      for {
        _ <- (1 to noImages).toList.traverse { idx =>
          repo.createBook(
            book.copy(
              title = show"book-$idx",
              isbn = show"isbn-$idx",
              thumbnailUri = imgUri
            ),
            constantDate.plusDays(idx.toLong)
          )
        }
        summary <- summaryService.summary(
          constantDate.some,
          constantDate.plusYears(1).some
        )
        b64 <- IO(Base64.getDecoder().decode(summary.montage))
        is  <- IO(new ByteArrayInputStream(b64))
        img <- IO(ImageIO.read(is))
      } yield expect(summary.added == noImages) and expect(
        Math
          .ceil(noImages.toDouble / montageSpecification.columns)
          .toInt * montageSpecification.smallImageHeight == img.getHeight()
      )
  }
}
