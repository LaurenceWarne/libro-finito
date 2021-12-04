package fin.service.summary

import scala.util.Random

import cats.effect.IO
import weaver._

import fin.Types._

object BufferedImageMontageServiceTest extends SimpleIOSuite {

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

  test("foobar") {
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
    val service = BufferedImageMontageService[IO](MontageSpecification())
    for {
      _ <- service.montage(books)
    } yield success
  }
}
