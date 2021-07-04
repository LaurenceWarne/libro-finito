package fin.service

import cats.effect.concurrent.Ref
import cats.implicits._
import cats.MonadError

import fin.persistence.CollectionRepository

import fin.implicits._
import fin.Types._

class InMemoryCollectionRepository[F[_]](
    collectionsRef: Ref[F, List[Collection]]
)(implicit me: MonadError[F, Throwable])
    extends CollectionRepository[F] {

  override def collections: F[List[Collection]] = collectionsRef.get

  override def createCollection(name: String): F[Collection] = {
    val collection = Collection(name, List.empty)
    collectionsRef.getAndUpdate(collection :: _).map(_ => collection)
  }

  override def collection(name: String): F[Option[Collection]] =
    collectionsRef.get.map(_.find(_.name === name))

  override def deleteCollection(name: String): F[Unit] =
    collectionsRef.getAndUpdate(_.filterNot(_.name === name)).void

  override def changeCollectionName(
      currentName: String,
      newName: String
  ): F[Collection] = {
    for {
      retrievedCollection <- collectionOrError(currentName)
      newCollection = Collection(newName, retrievedCollection.books)
      _ <- collectionsRef.getAndUpdate(_.map { col =>
        if (col === retrievedCollection) newCollection else col
      })
    } yield newCollection
  }

  override def addBookToCollection(
      collectionName: String,
      book: Book
  ): F[Collection] =
    for {
      retrievedCollection <- collectionOrError(collectionName)
      newCollection =
        Collection(collectionName, book :: retrievedCollection.books)
      _ <- collectionsRef.getAndUpdate(_.map { col =>
        if (col === retrievedCollection) newCollection else col
      })
    } yield newCollection

  override def removeBookFromCollection(
      collectionName: String,
      book: Book
  ): F[Unit] =
    for {
      retrievedCollection <- collectionOrError(collectionName)
      newCollection = Collection(
        collectionName,
        retrievedCollection.books.filterNot(_ === book)
      )
      _ <- collectionsRef.getAndUpdate(_.map { col =>
        if (col === retrievedCollection) newCollection else col
      })
    } yield ()

  private def collectionOrError(collectionName: String): F[Collection] =
    for {
      maybeCollection <- collection(collectionName)
      retrievedCollection <- MonadError[F, Throwable].fromOption(
        maybeCollection,
        new Exception(show"Collection '$collectionName' does not exist!")
      )
    } yield retrievedCollection

}
