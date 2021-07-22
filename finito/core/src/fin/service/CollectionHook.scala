package fin.service

import enumeratum._

sealed trait HookType extends EnumEntry

object HookType extends Enum[HookType] {
  val values = findValues

  case object ReadStarted   extends HookType
  case object ReadCompleted extends HookType
  case object Rate          extends HookType
  case object Add           extends HookType
}

case class CollectionHook(collection: String, `type`: HookType, code: String)
