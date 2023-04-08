package fin.service.book

import cats.effect.Sync
import cats.implicits._
import org.typelevel.log4cats.Logger

import fin.CollectionAlreadyExistsError
import fin.Types._
import fin.service.collection._

import HookType._
import Bindable._

class SpecialBookService[F[_]: Sync: Logger] private (
    wrappedCollectionService: CollectionService[F],
    wrappedBookService: BookManagementService[F],
    specialCollections: List[SpecialCollection],
    hookExecutionService: HookExecutionService[F]
) extends BookManagementService[F] {

  private val collectionHooks = specialCollections.flatMap(_.collectionHooks)

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
        case (hook, ProcessResult.Add) =>
          specialCollections
            .find(_.name === hook.collection)
            .traverse(sc => addHookCollection(sc, book))
        case (hook, ProcessResult.Remove) =>
          specialCollections
            .find(_.name === hook.collection)
            .traverse(sc => removeHookCollection(sc, book))
      }
    } yield ()

  private def addHookCollection(
      collection: SpecialCollection,
      book: BookInput
  ): F[Unit] = {
    Logger[F].info(
      show"Adding ${book.title} to special collection '${collection.name}'"
    ) *>
      createCollectionIfNotExists(collection.name, collection.preferredSort) *>
      wrappedCollectionService
        .addBookToCollection(
          MutationsAddBookArgs(collection.name.some, book)
        )
        .void
        .handleErrorWith { err =>
          Logger[F].error(
            show"""
               |Unable to add book to special collection '${collection.name}',
               |reason: ${err.getMessage}""".stripMargin.replace("\n", " ")
          )
        }
  }

  private def removeHookCollection(
      collection: SpecialCollection,
      book: BookInput
  ): F[Unit] = {
    Logger[F].info(
      show"Removing ${book.title} from special collection '${collection.name}'"
    ) *>
      createCollectionIfNotExists(collection.name, collection.preferredSort) *>
      wrappedCollectionService
        .removeBookFromCollection(
          MutationsRemoveBookArgs(collection.name, book.isbn)
        )
        .void
        .handleErrorWith { err =>
          Logger[F].error(
            show"""
               |Unable to remove book from special collection
               |'${collection.name}', reason: ${err.getMessage}""".stripMargin
              .replace("\n", " ")
          )
        }
  }

  private def createCollectionIfNotExists(
      collection: String,
      maybeSort: Option[Sort]
  ): F[Unit] =
    wrappedCollectionService
      .createCollection(
        MutationsCreateCollectionArgs(
          collection,
          None,
          maybeSort.map(_.`type`),
          maybeSort.map(_.sortAscending)
        )
      )
      .void
      .recover { case _: CollectionAlreadyExistsError => () }
}

object SpecialBookService {
  def apply[F[_]: Sync: Logger](
      wrappedCollectionService: CollectionService[F],
      wrappedBookService: BookManagementService[F],
      specialCollections: List[SpecialCollection],
      hookExecutionService: HookExecutionService[F]
  ) =
    new SpecialBookService[F](
      wrappedCollectionService,
      wrappedBookService,
      specialCollections,
      hookExecutionService
    )
}
