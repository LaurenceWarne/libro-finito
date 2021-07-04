package fin

import cats.kernel.Eq
import cats.Show

import fin.Types._

object implicits {
  implicit val CollectionEq: Eq[Collection]     = Eq.fromUniversalEquals
  implicit val BookEq: Eq[Book]                 = Eq.fromUniversalEquals
  implicit val CollectionShow: Show[Collection] = Show.fromToString
  implicit val BookShow: Show[Book]             = Show.fromToString
}
