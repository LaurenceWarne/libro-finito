package fin.api

import cats.effect._
import weaver.SimpleIOSuite

object GoogleBookAPITest extends SimpleIOSuite {

  test("description") {
    IO(expect(1 == 1))
  }

}
