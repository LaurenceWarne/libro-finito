package fin.service.summary

import java.io.{ByteArrayInputStream, File}
import java.util.Base64
import javax.imageio.ImageIO

import scala.util.Random

import cats.effect.IO
import cats.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.BookConversions._
import fin.NoBooksFoundForMontageError
import fin.Types._

object BufferedImageMontageServiceTest extends SimpleIOSuite {

  implicit def unsafeLogger: Logger[IO] = Slf4jLogger.getLogger
  def service                           = BufferedImageMontageService[IO]

  val uris = List(
    "http://books.google.com/books/content?id=sMHmCwAAQBAJ&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=OV4eQgAACAAJ&printsec=frontcover&img=1&zoom=1&source=gbs_api",
    "http://books.google.com/books/content?id=JYMLR4gzSR8C&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=E8Zp238yVY0C&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=reNQtm7Nv9kC&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=B91TKeLQ54EC&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=gnwETwF8Zb4C&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=_0YB05NPhJUC&printsec=frontcover&img=1&zoom=1&source=gbs_api",
    "http://books.google.com/books/content?id=TnzyrQEACAAJ&printsec=frontcover&img=1&zoom=1&source=gbs_api",
    "http://books.google.com/books/content?id=cIMdYAAACAAJ&printsec=frontcover&img=1&zoom=1&source=gbs_api",
    "http://books.google.com/books/content?id=kd1XlWVAIWQC&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=Jmv6DwAAQBAJ&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=75C5DAAAQBAJ&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=1FrJqcRILaoC&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=pilZDwAAQBAJ&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=oct4DwAAQBAJ&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=CVBObgUR2zcC&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=V5s14nks9I8C&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=MoEO9onVftUC&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api",
    "http://books.google.com/books/content?id=DTS-zQEACAAJ&printsec=frontcover&img=1&zoom=1&source=gbs_api"
  )

  test("montage runs successfully") {
    val books =
      uris.map(uri =>
        UserBook(
          uri.drop(38),
          List.empty,
          "",
          "",
          uri,
          None,
          Some(5 - (Random.nextInt() % 5)),
          None,
          None
        )
      )
    for {
      montage <- service.montage(books, None)
      b64     <- IO(Base64.getDecoder().decode(montage))
      is      <- IO(new ByteArrayInputStream(b64))
      img     <- IO(ImageIO.read(is))
      _       <- IO(ImageIO.write(img, "png", new File("montage.png")))
    } yield success
  }

  test("montage has image of correct height") {
    val noImages = 16
    val book =
      BookInput("title", List("author"), "cool description", "???", "uri")
    val imgUri =
      "https://user-images.githubusercontent.com/17688577/144673930-add9233d-9308-4972-8043-2f519d808874.png"
    val books = (1 to noImages).toList.map { idx =>
      toUserBook(
        book.copy(
          title = show"book-$idx",
          isbn = show"isbn-$idx",
          thumbnailUri = imgUri
        )
      )
    }
    for {
      montage <- service.montage(books, None)
      b64     <- IO(Base64.getDecoder().decode(montage))
      is      <- IO(new ByteArrayInputStream(b64))
      img     <- IO(ImageIO.read(is))
    } yield expect(
      Math
        .ceil(noImages.toDouble / MontageInputs.default.columns)
        .toInt * MontageInputs.smallImageHeight(MontageInputs.default) == img
        .getHeight()
    )
  }

  test("montage errors with no books") {
    for {
      result <- service.montage(List.empty, None).attempt
    } yield expect(result == NoBooksFoundForMontageError.asLeft)
  }
}
