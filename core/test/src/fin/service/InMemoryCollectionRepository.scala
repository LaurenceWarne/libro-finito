package fin.service

import cats.MonadError
import cats.effect.concurrent.Ref
import cats.implicits._

import fin.Types._
import fin.implicits._
import fin.persistence.CollectionRepository

class InMemoryCollectionRepository[F[_]](
    collectionsRef: Ref[F, List[Collection]]
)(implicit me: MonadError[F, Throwable])
    extends CollectionRepository[F] {

  override def collections: F[List[Collection]] = collectionsRef.get

  override def createCollection(name: String, preferredSort: Sort): F[Unit] = {
    val collection = Collection(name, List.empty, preferredSort)
    collectionsRef.getAndUpdate(collection :: _).void
  }

  override def collection(name: String): F[Option[Collection]] =
    collectionsRef.get.map(_.find(_.name === name))

  override def deleteCollection(name: String): F[Unit] =
    collectionsRef.getAndUpdate(_.filterNot(_.name === name)).void

  override def updateCollection(
      currentName: String,
      newName: String,
      preferredSort: Sort
  ): F[Unit] = {
    for {
      retrievedCollection <- collectionOrError(currentName)
      newCollection =
        retrievedCollection.copy(name = newName, preferredSort = preferredSort)
      _ <- collectionsRef.getAndUpdate(_.map { col =>
        if (col === retrievedCollection) newCollection else col
      })
    } yield ()
  }

  override def addBookToCollection(
      collectionName: String,
      book: Book
  ): F[Unit] =
    for {
      retrievedCollection <- collectionOrError(collectionName)
      newCollection =
        retrievedCollection.copy(books = book :: retrievedCollection.books)
      _ <- collectionsRef.getAndUpdate(_.map { col =>
        if (col === retrievedCollection) newCollection else col
      })
    } yield ()

  override def removeBookFromCollection(
      collectionName: String,
      isbn: String
  ): F[Unit] =
    for {
      retrievedCollection <- collectionOrError(collectionName)
      newCollection = retrievedCollection.copy(books =
        retrievedCollection.books.filterNot(_.isbn === isbn)
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
