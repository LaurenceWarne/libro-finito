package fin.service

import javax.script._

import scala.util.Try

import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.luaj.vm2.LuaBoolean

import fin.Types._
import fin.implicits._

import HookType._
import Bindable._

/**
  * This class manages special collection hooks on top of collection and
  * book management services.  Essentially, for each method, we run special
  * collection hooks and then delegate to the appropriate service.
  *
  * @param maybeDefaultCollection the default collection
  * @param wrappedCollectionService the wrapped collection service
  * @param wrappedBookService the wrapped book management service
  * @param collectionHooks collection hooks to run
  * @param scriptEngineManager the script engine manager
  */
class SpecialCollectionService[F[_]: Sync: Logger] private (
    maybeDefaultCollection: Option[String],
    wrappedCollectionService: CollectionService[F],
    wrappedBookService: BookManagementService[F],
    collectionHooks: List[CollectionHook],
    scriptEngineManager: ScriptEngineManager
) extends CollectionService[F]
    with BookManagementService[F] {

  override def createBook(args: MutationsCreateBookArgs): F[UserBook] =
    wrappedBookService.createBook(args)

  override def rateBook(args: MutationsRateBookArgs): F[UserBook] =
    for {
      response <- wrappedBookService.rateBook(args)
      bindings = Map("rating" -> args.rating).asBindings
      _ <- processHooks(_.`type` === HookType.Rate, bindings, args.book)
    } yield response

  override def startReading(args: MutationsStartReadingArgs): F[UserBook] =
    for {
      response <- wrappedBookService.startReading(args)
      _ <- processHooks(
        _.`type` === HookType.ReadStarted,
        SBindings.empty,
        args.book
      )
    } yield response

  override def finishReading(args: MutationsFinishReadingArgs): F[UserBook] =
    for {
      response <- wrappedBookService.finishReading(args)
      _ <- processHooks(
        _.`type` === HookType.ReadCompleted,
        SBindings.empty,
        args.book
      )
    } yield response

  override def deleteBookData(args: MutationsDeleteBookDataArgs): F[Unit] =
    wrappedBookService.deleteBookData(args)

  override def collections: F[List[Collection]] =
    wrappedCollectionService.collections

  override def createCollection(
      args: MutationsCreateCollectionArgs
  ): F[Collection] = wrappedCollectionService.createCollection(args)

  override def collection(
      args: QueriesCollectionArgs
  ): F[Collection] = wrappedCollectionService.collection(args)

  override def deleteCollection(
      args: MutationsDeleteCollectionArgs
  ): F[Unit] =
    Sync[F].whenA(
      collectionHooks.exists(_.collection === args.name)
    )(Sync[F].raiseError(CannotDeleteSpecialCollectionError)) *>
      wrappedCollectionService.deleteCollection(args)

  override def updateCollection(
      args: MutationsUpdateCollectionArgs
  ): F[Collection] =
    Sync[F].whenA(
      args.newName.nonEmpty && collectionHooks.exists(
        _.collection === args.currentName
      )
    )(Sync[F].raiseError(CannotChangeNameOfSpecialCollectionError)) *>
      wrappedCollectionService.updateCollection(args)

  override def addBookToCollection(
      args: MutationsAddBookArgs
  ): F[Collection] = {
    val maybeCollectionName = args.collection.orElse(maybeDefaultCollection)
    for {
      collectionName <- Sync[F].fromOption(
        maybeCollectionName,
        DefaultCollectionNotSupportedError
      )
      response <- wrappedCollectionService.addBookToCollection(
        args.copy(collection = collectionName.some)
      )
      _ <- processHooks(
        h =>
          h.`type` === HookType.Add && args.collection.exists(
            _ =!= h.collection
          ),
        Map("collection" -> collectionName).asBindings |+| args.book.asBindings,
        args.book
      )
    } yield response
  }

  override def removeBookFromCollection(
      args: MutationsRemoveBookArgs
  ): F[Unit] = wrappedCollectionService.removeBookFromCollection(args)

  private def processHooks(
      hookFilter: CollectionHook => Boolean,
      additionalBindings: SBindings,
      book: BookInput
  ): F[Unit] =
    for {
      engine <- Sync[F].delay(scriptEngineManager.getEngineByName("luaj"))
      _ <-
        collectionHooks
          .filter(hookFilter)
          .traverse(hook => {
            for {
              hookResponse <- processHook(hook, engine, additionalBindings)
              _ <- hookResponse.traverse {
                case ProcessResult.Add    => addHookCollection(hook, book)
                case ProcessResult.Remove => removeHookCollection(hook, book)
              }
            } yield ()
          })
    } yield ()

  private def createCollectionIfNotExists(collection: String): F[Unit] =
    createCollection(MutationsCreateCollectionArgs(collection, None)).void
      .handleError(_ => ())

  private def addHookCollection(
      hook: CollectionHook,
      book: BookInput
  ): F[Unit] = {
    Logger[F].info(
      show"Adding $book to special collection '${hook.collection}'"
    ) *>
      createCollectionIfNotExists(hook.collection) *>
      wrappedCollectionService
        .addBookToCollection(
          MutationsAddBookArgs(hook.collection.some, book)
        )
        .void
        .handleErrorWith(err =>
          Logger[F].error(
            show"""
               |Unable to add book to special collection '${hook.collection}',
               |reason: ${err.getMessage}""".stripMargin.replace("\n", " ")
          )
        )
  }

  private def removeHookCollection(
      hook: CollectionHook,
      book: BookInput
  ): F[Unit] = {
    Logger[F].info(
      show"Removing $book from special collection '${hook.collection}'"
    ) *>
      wrappedCollectionService
        .removeBookFromCollection(
          MutationsRemoveBookArgs(hook.collection, book.isbn)
        )
        .void
        .handleErrorWith(err =>
          Logger[F].error(
            show"""
               |Unable to remove book from special collection
               |'${hook.collection}', reason: ${err.getMessage}""".stripMargin
              .replace("\n", " ")
          )
        )
  }

  private def processHook(
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

object SpecialCollectionService {
  def apply[F[_]: Sync: Logger](
      maybeDefaultCollection: Option[String],
      wrappedCollectionService: CollectionService[F],
      wrappedBookService: BookManagementService[F],
      collectionHooks: List[CollectionHook],
      scriptEngineManager: ScriptEngineManager
  ) =
    new SpecialCollectionService[F](
      maybeDefaultCollection,
      wrappedCollectionService,
      wrappedBookService,
      collectionHooks,
      scriptEngineManager
    )
}

sealed trait ProcessResult

object ProcessResult {
  case object Add    extends ProcessResult
  case object Remove extends ProcessResult
}

case object CannotChangeNameOfSpecialCollectionError extends Throwable {
  override def getMessage = "Cannot update the name of a special collection"
}

case object CannotDeleteSpecialCollectionError extends Throwable {
  override def getMessage =
    """
     |Cannot delete a special collection!  In order to delete a special
     |collection, first remove it's special collection definition from your
     |config file, and then delete it.""".stripMargin.replace("\n", " ")
}
