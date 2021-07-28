package fin

import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger

import fin.Types._
import fin.config.SpecialCollection
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
          .traverse { collection =>
            Logger[F].info(
              show"Creating collection marked as not lazy: '${collection.name}'"
            ) *>
              collectionService
                .createCollection(
                  MutationsCreateCollectionArgs(collection.name, None)
                )
                .void
                // TODO use error classes here so we don't catch all errors
                .handleErrorWith(_ =>
                  Logger[F]
                    .info(show"Collection '${collection.name}' already exists")
                )
          }
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
}
