package fin

import cats.effect.Sync
import cats.implicits._
import cats.{Monad, ~>}
import org.typelevel.log4cats.Logger

import fin.implicits._
import fin.persistence.CollectionRepository
import fin.service.book._
import fin.service.collection._

object SpecialCollectionSetup {
  def setup[F[_]: Sync: Logger, G[_]: Monad](
      collectionRepo: CollectionRepository[G],
      collectionService: CollectionService[F],
      bookService: BookManagementService[F],
      defaultCollection: Option[String],
      specialCollections: List[SpecialCollection],
      transact: G ~> F
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
            transact(processSpecialCollection[G](collectionRepo, c))
              .flatMap(Logger[F].info(_))
          }
      hookExecutionService = HookExecutionServiceImpl[F]
      wrappedCollectionService = SpecialCollectionService[F](
        defaultCollection,
        collectionService,
        specialCollections,
        hookExecutionService
      )
      wrappedBookService = SpecialBookService[F](
        collectionService,
        bookService,
        specialCollections,
        hookExecutionService
      )
    } yield (wrappedBookService, wrappedCollectionService)

  private def processSpecialCollection[G[_]: Monad](
      collectionRepo: CollectionRepository[G],
      collection: SpecialCollection
  ): G[String] =
    for {
      maybeCollection <- collectionRepo.collection(collection.name)
      _ <- Monad[G].whenA(maybeCollection.isEmpty) {
        val sort = collection.preferredSort.getOrElse(
          CollectionServiceImpl.defaultSort
        )
        collectionRepo.createCollection(collection.name, sort)
      }
      maybeUpdatedCollection <-
        maybeCollection
          .zip(collection.preferredSort)
          .collect {
            case (c, sort) if c.preferredSort =!= sort => sort
          }
          .traverse { sort =>
            collectionRepo
              .updateCollection(collection.name, collection.name, sort)
          }
    } yield maybeUpdatedCollection
      .as(show"Updated collection '${collection.name}'")
      .orElse(
        maybeCollection.as(
          show"No changes for special collection '${collection.name}'"
        )
      )
      .getOrElse(
        show"Created collection marked as not lazy: '${collection.name}'"
      )
}
