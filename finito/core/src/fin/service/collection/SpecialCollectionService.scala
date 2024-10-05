package fin.service.collection

import cats.Show
import cats.effect.Sync
import cats.implicits._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import org.typelevel.log4cats.Logger

import fin.Types._
import fin._
import fin.implicits._

import HookType._
import Bindable._

/** This class manages special collection hooks on top of a collection service.
  * Essentially, for each method, we run special collection hooks and then
  * delegate to the service.
  *
  * @param maybeDefaultCollection
  *   the default collection
  * @param wrappedCollectionService
  *   the wrapped collection service
  * @param specialCollections
  *   special collections
  * @param hookExecutionService
  *   the hook execution service
  */
class SpecialCollectionService[F[_]: Sync: Logger] private (
    maybeDefaultCollection: Option[String],
    wrappedCollectionService: CollectionService[F],
    specialCollections: List[SpecialCollection],
    hookExecutionService: HookExecutionService[F]
) extends CollectionService[F] {

  private val collectionHooks = specialCollections.flatMap(_.collectionHooks)

  override def collections: F[List[Collection]] =
    wrappedCollectionService.collections

  override def createCollection(
      args: MutationCreateCollectionArgs
  ): F[Collection] = wrappedCollectionService.createCollection(args)

  override def collection(
      args: QueryCollectionArgs
  ): F[Collection] = wrappedCollectionService.collection(args)

  override def deleteCollection(
      args: MutationDeleteCollectionArgs
  ): F[Unit] =
    Sync[F].raiseWhen(
      collectionHooks.exists(_.collection === args.name)
    )(CannotDeleteSpecialCollectionError) *>
      wrappedCollectionService.deleteCollection(args)

  override def updateCollection(
      args: MutationUpdateCollectionArgs
  ): F[Collection] =
    Sync[F].raiseWhen(
      args.newName.nonEmpty && collectionHooks.exists(
        _.collection === args.currentName
      )
    )(CannotChangeNameOfSpecialCollectionError) *>
      wrappedCollectionService.updateCollection(args)

  override def addBookToCollection(
      args: MutationAddBookArgs
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
      hookResponses <- hookExecutionService.processHooks(
        collectionHooks.filter { h =>
          h.`type` === HookType.Add && args.collection.exists(
            _ =!= h.collection
          )
        },
        Map("collection" -> collectionName).asBindings |+| args.book.asBindings,
        args.book
      )
      _ <- hookResponses.traverse {
        case (hook, ProcessResult.Add) =>
          specialCollections
            .find(_.name === hook.collection)
            .traverse(sc => addHookCollection(sc, args.book))
        case (hook, ProcessResult.Remove) =>
          specialCollections
            .find(_.name === hook.collection)
            .traverse(sc => removeHookCollection(sc, args.book))
      }
    } yield response
  }

  override def removeBookFromCollection(
      args: MutationRemoveBookArgs
  ): F[Unit] = wrappedCollectionService.removeBookFromCollection(args)

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
          MutationAddBookArgs(collection.name.some, book)
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
      wrappedCollectionService
        .removeBookFromCollection(
          MutationRemoveBookArgs(collection.name, book.isbn)
        )
        .void
        .handleErrorWith { err =>
          Logger[F].error(
            // TODO don't log error if it's a CollectionAlreadyExistsError
            // use .recover instead
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
    createCollection(
      MutationCreateCollectionArgs(
        collection,
        None,
        maybeSort.map(_.`type`),
        maybeSort.map(_.sortAscending)
      )
    ).void.recover { case _: CollectionAlreadyExistsError => () }
}

object SpecialCollectionService {
  def apply[F[_]: Sync: Logger](
      maybeDefaultCollection: Option[String],
      wrappedCollectionService: CollectionService[F],
      specialCollections: List[SpecialCollection],
      hookExecutionService: HookExecutionService[F]
  ) =
    new SpecialCollectionService[F](
      maybeDefaultCollection,
      wrappedCollectionService,
      specialCollections,
      hookExecutionService
    )

}

final case class SpecialCollection(
    name: String,
    `lazy`: Option[Boolean],
    addHook: Option[String],
    readStartedHook: Option[String],
    readCompletedHook: Option[String],
    rateHook: Option[String],
    preferredSort: Option[Sort]
) {
  def collectionHooks: List[CollectionHook] =
    (addHook.map(CollectionHook(name, HookType.Add, _)) ++
      readStartedHook.map(CollectionHook(name, HookType.ReadStarted, _)) ++
      readCompletedHook.map(CollectionHook(name, HookType.ReadCompleted, _)) ++
      rateHook.map(CollectionHook(name, HookType.Rate, _))).toList
}

object SpecialCollection {
  implicit val specialCollectionShow: Show[SpecialCollection] =
    Show.fromToString

  implicit val customConfig: Configuration =
    Configuration.default.withKebabCaseMemberNames
  implicit val specialCollectionDecoder: Decoder[SpecialCollection] =
    deriveConfiguredDecoder
}
