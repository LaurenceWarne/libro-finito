package fin.service.summary

import weaver.IOSuite
import org.http4s.blaze.client.BlazeClientBuilder
import cats.effect.IO
import org.http4s.client.Client
import fin.Types._

object BufferedImageMontageServiceTest extends IOSuite {

  override type Res = Client[IO]
  override def sharedResource = BlazeClientBuilder[IO].resource

  test("foobar") { client =>
    val uri =
      "http://books.google.com/books/content?id=jUX8N9kiCiQC&printsec=frontcover&img=1&zoom=1&source=gbs_api"
    val book = UserBook("am", List.empty, "", "", uri, None, None, None, None)
    val service =
      new BufferedImageMontageService[IO](client, MontageSpecification())
    println(book)
    println(client)
    for {
      _ <- service.montage(List(book))
    } yield success
  }
}
