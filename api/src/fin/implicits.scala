package fin

import cats.kernel.Eq
import cats.Show

import fin.Types._

object implicits {
  implicit val collectionEq: Eq[Collection] = Eq.fromUniversalEquals
  implicit val bookEq: Eq[Book]             = Eq.fromUniversalEquals
  implicit val sortEq: Eq[Sort]             = Eq.fromUniversalEquals

  implicit val sortShow: Show[Sort]             = Show.fromToString
  implicit val collectionShow: Show[Collection] = Show.fromToString
  implicit val bookShow: Show[Book]             = Show.fromToString
}
