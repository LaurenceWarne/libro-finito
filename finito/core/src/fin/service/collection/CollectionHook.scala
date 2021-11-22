package fin.service.collection

import cats.Eq

sealed trait HookType extends Product with Serializable

object HookType {
  implicit val hookTypeEq: Eq[HookType] = Eq.fromUniversalEquals

  case object ReadStarted   extends HookType
  case object ReadCompleted extends HookType
  case object Rate          extends HookType
  case object Add           extends HookType
}

final case class CollectionHook(
    collection: String,
    `type`: HookType,
    code: String
)
