package fin.service.book

import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger

import fin.Types._
import fin.implicits._
import fin.service.collection._

import HookType._
import Bindable._

class SpecialBookService[F[_]: Sync: Logger] private (
    wrappedCollectionService: CollectionService[F],
    wrappedBookService: BookManagementService[F],
    collectionHooks: List[CollectionHook],
    hookExecutionService: HookExecutionService[F]
) extends BookManagementService[F] {

  override def createBook(args: MutationsCreateBookArgs): F[UserBook] =
    wrappedBookService.createBook(args)

  override def rateBook(args: MutationsRateBookArgs): F[UserBook] =
    for {
      response <- wrappedBookService.rateBook(args)
      bindings = Map("rating" -> args.rating).asBindings
      _ <- processHooks(
        collectionHooks.filter(_.`type` === HookType.Rate),
        bindings,
        args.book
      )
    } yield response

  override def startReading(args: MutationsStartReadingArgs): F[UserBook] =
    for {
      response <- wrappedBookService.startReading(args)
      _ <- processHooks(
        collectionHooks.filter(_.`type` === HookType.ReadStarted),
        SBindings.empty,
        args.book
      )
    } yield response

  override def finishReading(args: MutationsFinishReadingArgs): F[UserBook] =
    for {
      response <- wrappedBookService.finishReading(args)
      _ <- processHooks(
        collectionHooks.filter(_.`type` === HookType.ReadCompleted),
        SBindings.empty,
        args.book
      )
    } yield response

  override def deleteBookData(args: MutationsDeleteBookDataArgs): F[Unit] =
    wrappedBookService.deleteBookData(args)

  private def processHooks(
      hooks: List[CollectionHook],
      bindings: SBindings,
      book: BookInput
  ): F[Unit] =
    for {
      hookResponses <- hookExecutionService.processHooks(hooks, bindings, book)
      _ <- hookResponses.traverse {
        case (hook, ProcessResult.Add)    => addHookCollection(hook, book)
        case (hook, ProcessResult.Remove) => removeHookCollection(hook, book)
      }
    } yield ()

  private def createCollectionIfNotExists(collection: String): F[Unit] =
    wrappedCollectionService
      .createCollection(MutationsCreateCollectionArgs(collection, None))
      .void
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
}

object SpecialBookService {
  def apply[F[_]: Sync: Logger](
      wrappedCollectionService: CollectionService[F],
      wrappedBookService: BookManagementService[F],
      collectionHooks: List[CollectionHook],
      hookExecutionService: HookExecutionService[F]
  ) =
    new SpecialBookService[F](
      wrappedCollectionService,
      wrappedBookService,
      collectionHooks,
      hookExecutionService
    )
}
