package fin

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import org.typelevel.log4cats.Logger

import fin.Types._
import fin.config.SpecialCollection
import fin.implicits._
import fin.service.book._
import fin.service.collection._

object SpecialCollectionSetup {
  def setup[F[_]: Sync: Logger](
      collectionService: CollectionService[F],
      bookService: BookManagementService[F],
      defaultCollection: Option[String],
      specialCollections: List[SpecialCollection]
  ): F[(BookManagementService[F], CollectionService[F])] =
    for {
      _ <- Logger[F].info(
        "Found special collections: " + specialCollections
          .map(_.name)
          .mkString(", ")
      )
      _ <-
        specialCollections
          .filter(_.`lazy`.contains(false))
          .traverse { c =>
            createCollection[F](collectionService, c).semiflatMap { c2 =>
              updateCollection(collectionService, c, c2)
            }
          }
          .value
      hookExecutionService = HookExecutionServiceImpl[F]
      collectionHooks      = specialCollections.flatMap(_.toCollectionHooks)
      wrappedCollectionService = SpecialCollectionService[F](
        defaultCollection,
        collectionService,
        collectionHooks,
        hookExecutionService
      )
      wrappedBookService = SpecialBookService[F](
        collectionService,
        bookService,
        collectionHooks,
        hookExecutionService
      )
    } yield (wrappedBookService, wrappedCollectionService)

  private def createCollection[F[_]: Sync: Logger](
      collectionService: CollectionService[F],
      collection: SpecialCollection
  ): OptionT[F, Collection] = {
    val maybeCollectionF = Logger[F].info(
      show"Creating collection marked as not lazy: '${collection.name}'"
    ) *>
      collectionService
        .createCollection(
          MutationsCreateCollectionArgs(collection.name, None)
        )
        .void
        .recoverWith {
          case _: CollectionAlreadyExistsError =>
            Logger[F]
              .info(
                show"Collection '${collection.name}' already exists"
              )
        } *>
      collectionService
        .collection(QueriesCollectionArgs(collection.name))
        .attempt
        .map(_.toOption)
    OptionT(maybeCollectionF)
  }

  private def updateCollection[F[_]: Sync: Logger](
      collectionService: CollectionService[F],
      collection: SpecialCollection,
      existingCollection: Collection
  ): F[Unit] =
    collectionService
      .updateCollection(
        MutationsUpdateCollectionArgs(
          existingCollection.name,
          None,
          collection.sort.map(_.`type`),
          collection.sort.map(_.sortAscending)
        )
      ) *> Logger[F].info(
      show"Changed sort of ${existingCollection.name} to ${collection.sort}"
    )
}
