package fin.service

import cats.effect.Sync
import cats.implicits._

import fin.Types._
import fin.implicits._
import fin.persistence.CollectionRepository

import CollectionServiceImpl._

class CollectionServiceImpl[F[_]: Sync] private (
    collectionRepo: CollectionRepository[F]
) extends CollectionService[F] {

  override def collections: F[List[Collection]] = collectionRepo.collections

  override def createCollection(
      args: MutationsCreateCollectionArgs
  ): F[Collection] =
    for {
      maybeExistingCollection <- collectionRepo.collection(args.name)
      _ <- maybeExistingCollection.fold(
        collectionRepo.createCollection(args.name, defaultSort)
      ) { collection =>
        Sync[F].raiseError(
          new Exception(show"Collection already exists: $collection")
        )
      }
    } yield Collection(
      args.name,
      args.books.getOrElse(List.empty),
      defaultSort
    )

  override def collection(
      args: QueriesCollectionArgs
  ): F[Collection] = collectionOrError(args.name)

  override def deleteCollection(
      args: MutationsDeleteCollectionArgs
  ): F[Unit] = collectionRepo.deleteCollection(args.name)

  override def updateCollection(
      args: MutationsUpdateCollectionArgs
  ): F[Collection] =
    for {
      collection <- collectionOrError(args.currentName)
      _ <- Sync[F].whenA(args.newName.orElse(args.preferredSort).isEmpty) {
        Sync[F].raiseError(NotEnoughArgumentsForUpdateError)
      }
      _ <- args.newName.traverse(errorIfCollectionExists)
      _ <- collectionRepo.updateCollection(
        args.currentName,
        args.newName.getOrElse(collection.name),
        args.preferredSort.getOrElse(collection.preferredSort)
      )
    } yield collection.copy(
      name = args.newName.getOrElse(collection.name),
      preferredSort = args.preferredSort.getOrElse(collection.preferredSort)
    )

  override def addBookToCollection(
      args: MutationsAddBookArgs
  ): F[Collection] =
    for {
      collection <- collectionOrError(args.collection)
      _          <- collectionRepo.addBookToCollection(args.collection, args.book)
    } yield collection.copy(books = args.book :: collection.books)

  override def removeBookFromCollection(
      args: MutationsRemoveBookArgs
  ): F[Unit] =
    for {
      collection <- collectionOrError(args.collection)
      _ <- collectionRepo.removeBookFromCollection(
        args.collection,
        args.isbn
      )
    } yield collection.copy(books =
      collection.books.filterNot(_.isbn === args.isbn)
    )

  private def collectionOrError(collection: String): F[Collection] =
    for {
      maybeCollection <- collectionRepo.collection(collection)
      collection <- Sync[F].fromOption(
        maybeCollection,
        new Exception(show"Collection '$collection' does not exist!")
      )
    } yield collection

  private def errorIfCollectionExists(collection: String): F[Unit] =
    for {
      maybeExistingCollection <- collectionRepo.collection(collection)
      _ <- Sync[F].whenA(maybeExistingCollection.nonEmpty)(
        Sync[F].raiseError(
          new Exception(
            show"A collection with the name '${collection}' already exists!"
          )
        )
      )
    } yield ()
}

object CollectionServiceImpl {

  val defaultSort: Sort = Sort.DateAdded

  def apply[F[_]: Sync](collectionRepo: CollectionRepository[F]) =
    new CollectionServiceImpl[F](collectionRepo)
}

case object NotEnoughArgumentsForUpdateError extends Throwable {
  override def getMessage =
    "At least one of 'newName' and 'preferredSort' must be specified"
}