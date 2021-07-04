package fin.service

import cats.effect.Sync
import cats.implicits._

import fin.Types._
import fin.implicits._
import fin.persistence.CollectionRepository

class CollectionServiceImpl[F[_]: Sync](collectionRepo: CollectionRepository[F])
    extends CollectionService[F] {

  override def collections: F[List[Collection]] = collectionRepo.collections

  override def createCollection(
      args: MutationsCreateCollectionArgs
  ): F[Collection] =
    for {
      maybeExistingCollection <- collectionRepo.collection(args.name)
      _ <- maybeExistingCollection.fold(
        collectionRepo.createCollection(args.name)
      ) { collection =>
        Sync[F].raiseError(
          new Exception(show"Collection already exists: $collection")
        )
      }
    } yield Collection(args.name, args.books.getOrElse(List.empty[Book]))

  override def collection(
      args: QueriesCollectionArgs
  ): F[Collection] = collectionOrError(args.name)

  override def deleteCollection(
      args: MutationsDeleteCollectionArgs
  ): F[Unit] = collectionRepo.deleteCollection(args.name)

  override def changeCollectionName(
      args: MutationsChangeCollectionNameArgs
  ): F[Collection] =
    for {
      collection <- collectionOrError(args.currentName)
      _          <- collectionRepo.changeCollectionName(args.currentName, args.newName)
    } yield Collection(args.newName, collection.books)

  override def addBookToCollection(
      args: MutationsAddBookArgs
  ): F[Collection] =
    for {
      collection <- collectionOrError(args.collection)
      _          <- collectionRepo.addBookToCollection(args.collection, args.book)
    } yield Collection(args.collection, args.book :: collection.books)

  override def removeBookFromCollection(
      args: MutationsRemoveBookArgs
  ): F[Unit] =
    for {
      collection <- collectionOrError(args.collection)
      _          <- collectionRepo.removeBookFromCollection(args.collection, args.book)
    } yield Collection(
      args.collection,
      collection.books.filterNot(_ === args.book)
    )

  private def collectionOrError(collection: String): F[Collection] =
    for {
      maybeCollection <- collectionRepo.collection(collection)
      collection <- Sync[F].fromOption(
        maybeCollection,
        new Exception(show"Collection '$collection' does not exist!")
      )
    } yield collection
}
