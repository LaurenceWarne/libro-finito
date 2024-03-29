package fin

import cats.effect.Sync
import cats.implicits._
import cats.{Monad, ~>}
import org.typelevel.log4cats.Logger

import fin.implicits._
import fin.persistence.CollectionRepository
import fin.service.book._
import fin.service.collection.SpecialCollection._
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
      _ <- Logger[F].debug(show"Special collection info: $specialCollections")
      _ <- transact(
        specialCollections
          .traverse(c => processSpecialCollection[G](collectionRepo, c))
      ).flatMap(_.traverse(s => Logger[F].info(s)))
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
      maybeCollection <-
        collectionRepo.collection(collection.name, 1.some, 0.some)
      createCollection =
        maybeCollection.isEmpty && collection.`lazy`.contains(false)
      _ <- Monad[G].whenA(createCollection) {
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
        if (createCollection)
          show"Created collection marked as not lazy: '${collection.name}'"
        else show"Left lazy collection '${collection.name}' unitialized"
      )
}
