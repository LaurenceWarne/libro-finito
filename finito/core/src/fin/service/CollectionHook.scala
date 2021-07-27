package fin.service

import cats.Eq
import enumeratum._

sealed trait HookType extends EnumEntry

object HookType extends Enum[HookType] {
  val values = findValues

  case object ReadStarted   extends HookType
  case object ReadCompleted extends HookType
  case object Rate          extends HookType
  case object Add           extends HookType

  implicit val hookTypeEq: Eq[HookType] = Eq.fromUniversalEquals
}

final case class CollectionHook(
    collection: String,
    `type`: HookType,
    code: String
)
