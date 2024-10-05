package fin.service.collection

import javax.script._

import scala.util.Try

import cats.effect.Sync
import cats.implicits._
import org.luaj.vm2.LuaBoolean
import org.typelevel.log4cats.Logger

import fin.Types._

trait HookExecutionService[F[_]] {
  def processHooks(
      hooks: List[CollectionHook],
      additionalBindings: SBindings,
      book: BookInput
  ): F[List[(CollectionHook, ProcessResult)]]
}

class HookExecutionServiceImpl[F[_]: Sync: Logger] private ()
    extends HookExecutionService[F] {

  private val scriptEngineManager: ScriptEngineManager = new ScriptEngineManager

  override def processHooks(
      collectionHooks: List[CollectionHook],
      additionalBindings: SBindings,
      book: BookInput
  ): F[List[(CollectionHook, ProcessResult)]] =
    for {
      engine <- Sync[F].delay(scriptEngineManager.getEngineByName("luaj"))
      results <-
        collectionHooks
          .traverse { hook =>
            processHook(hook, engine, additionalBindings)
              .tupleLeft(hook)
              .flatTap { case (hook, result) =>
                Logger[F].debug(
                  s"Hook for ${hook.collection} ran with result $result"
                )
              }

          }
    } yield results.collect { case (hook, Some(result)) => (hook, result) }

  def processHook(
      hook: CollectionHook,
      engine: ScriptEngine,
      bindings: SBindings
  ): F[Option[ProcessResult]] = {
    val allBindings = bindings.asJava
    for {
      _      <- Sync[F].delay(engine.eval(hook.code, allBindings))
      addStr <- Sync[F].delay(allBindings.get("add"))
      rmStr  <- Sync[F].delay(allBindings.get("remove"))
      maybeAdd = Try(
        Option(addStr.asInstanceOf[LuaBoolean])
      ).toOption.flatten.map(_.booleanValue)
      maybeRemove = Try(
        Option(rmStr.asInstanceOf[LuaBoolean])
      ).toOption.flatten.map(_.booleanValue)
    } yield maybeAdd
      .collect { case true => ProcessResult.Add }
      .orElse(maybeRemove.collect { case true => ProcessResult.Remove })
  }
}

object HookExecutionServiceImpl {
  def apply[F[_]: Sync: Logger] = new HookExecutionServiceImpl[F]
}

sealed trait ProcessResult extends Product with Serializable

object ProcessResult {
  case object Add    extends ProcessResult
  case object Remove extends ProcessResult
}
