package fin.service

import scala.util.Try

import cats.effect.Sync
import cats.implicits._
import javax.script._

import fin.Types._

import ProcessResult._

class SpecialCollectionService[F[_]: Sync](
    wrappedService: CollectionService[F],
    collectionHooks: List[CollectionHook],
    scriptEngineManager: ScriptEngineManager
) extends CollectionService[F] {

  override def collections: F[List[Collection]] =
    wrappedService.collections

  override def createCollection(
      args: MutationsCreateCollectionArgs
  ): F[Collection] = wrappedService.createCollection(args)

  override def collection(
      args: QueriesCollectionArgs
  ): F[Collection] = wrappedService.collection(args)

  override def deleteCollection(
      args: MutationsDeleteCollectionArgs
  ): F[Unit] = wrappedService.deleteCollection(args)

  override def changeCollectionName(
      args: MutationsChangeCollectionNameArgs
  ): F[Collection] = wrappedService.changeCollectionName(args)

  override def addBookToCollection(
      args: MutationsAddBookArgs
  ): F[Collection] =
    for {
      resp   <- wrappedService.addBookToCollection(args)
      engine <- Sync[F].delay(scriptEngineManager.getEngineByName("luaj"))
      _ <-
        collectionHooks
          .filter { hook =>
            hook.`type` == HookType.Add && hook.collection != args.collection
          }
          .traverse(hook => {
            for {
              bindings     <- bindings(args.collection, args.book)
              hookResponse <- processHook(hook, engine, bindings)
              _ <- hookResponse.traverse {
                case Add    => addHookCollection(hook, args.book)
                case Remove => removeHookCollection(hook, args.book)
              }
            } yield ()
          })
    } yield resp

  override def removeBookFromCollection(
      args: MutationsRemoveBookArgs
  ): F[Unit] = wrappedService.removeBookFromCollection(args)

  private def addHookCollection(hook: CollectionHook, book: Book): F[Unit] = {
    wrappedService
      .addBookToCollection(
        MutationsAddBookArgs(hook.collection, book)
      )
      .void
  }

  private def removeHookCollection(
      hook: CollectionHook,
      book: Book
  ): F[Unit] = {
    wrappedService
      .removeBookFromCollection(
        MutationsRemoveBookArgs(hook.collection, book.isbn)
      )
      .void
  }

  private def bindings(collection: String, book: Book): F[Bindings] = {
    for {
      bindings <- Sync[F].delay(new SimpleBindings)
      _        <- Sync[F].delay(bindings.put("collection", collection))
      _        <- Sync[F].delay(bindings.put("title", book.title))
      _        <- Sync[F].delay(bindings.put("authors", book.authors))
      _        <- Sync[F].delay(bindings.put("isbn", book.isbn))
    } yield bindings
  }

  private def processHook(
      hook: CollectionHook,
      engine: ScriptEngine,
      bindings: Bindings
  ): F[Option[ProcessResult]] =
    for {
      _      <- Sync[F].delay(engine.eval(hook.code, bindings))
      addStr <- Sync[F].delay(engine.get("add"))
      rmStr  <- Sync[F].delay(engine.get("remove"))
      maybeAdd    = Try(Option(addStr.asInstanceOf[Boolean])).toOption.flatten
      maybeRemove = Try(Option(rmStr.asInstanceOf[Boolean])).toOption.flatten
    } yield maybeAdd
      .collect { case true => Add }
      .orElse(maybeRemove.collect { case true => Remove })
}

sealed trait ProcessResult

object ProcessResult {
  case object Add    extends ProcessResult
  case object Remove extends ProcessResult
}
