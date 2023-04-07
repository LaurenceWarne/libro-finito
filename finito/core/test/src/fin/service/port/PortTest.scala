package fin.service.port

import cats.effect._
import weaver._

object PortTest extends SimpleIOSuite {

  test("foo") {
    for {
      collection <- new GoodreadsImport[IO]().importResource(
        "./assets/sample_goodreads_export.csv"
      )
      _ = println(collection)
    } yield success
  }
}
