package fin.service.port

import weaver._
import cats.effect._

object PortTest extends SimpleIOSuite {

  test("foo") {
    for {
      collection <- new GoodreadsImport[IO]().importResource(
        "/home/laurencewarne/projects/libro-finito/assets/sample_goodreads_export.csv"
      )
      _ = println(collection)
    } yield success
  }
}
