package fin

import cats.kernel.Eq
import cats.Show

import fin.Types._

object implicits {
  implicit val collectionEq: Eq[Collection] = Eq.fromUniversalEquals
  implicit val bookEq: Eq[BookInput]        = Eq.fromUniversalEquals
  implicit val userBookEq: Eq[UserBook]     = Eq.fromUniversalEquals
  implicit val sortEq: Eq[Sort]             = Eq.fromUniversalEquals
  implicit val summaryEq: Eq[Summary]       = Eq.fromUniversalEquals

  implicit val collectionShow: Show[Collection] = Show.fromToString
  implicit val userBookShow: Show[BookInput]    = Show.fromToString
  implicit val bookShow: Show[UserBook]         = Show.fromToString
  implicit val sortShow: Show[Sort]             = Show.fromToString
  implicit val summaryShow: Show[Summary] = s =>
    s.copy(montage = "<base64>").toString
}
