package fin.service.collection

import java.time.LocalDate

import cats.effect.Ref
import cats.implicits._
import cats.{MonadError, MonadThrow}

import fin.BookConversions._
import fin.Types._
import fin._
import fin.persistence.CollectionRepository

class InMemoryCollectionRepository[F[_]: MonadThrow](
    collectionsRef: Ref[F, List[Collection]]
) extends CollectionRepository[F] {

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
      _ <- collectionOrError(currentName)
      _ <- collectionsRef.getAndUpdate(_.map { col =>
        if (col.name === currentName)
          col.copy(name = newName, preferredSort = preferredSort)
        else col
      })
    } yield ()
  }

  override def addBookToCollection(
      collectionName: String,
      book: BookInput,
      date: LocalDate
  ): F[Unit] =
    for {
      _ <- collectionOrError(collectionName)
      _ <- collectionsRef.getAndUpdate(_.map { col =>
        if (col.name === collectionName)
          col.copy(books = toUserBook(book) :: col.books)
        else col
      })
    } yield ()

  override def removeBookFromCollection(
      collectionName: String,
      isbn: String
  ): F[Unit] =
    for {
      _ <- collectionOrError(collectionName)
      _ <- collectionsRef.getAndUpdate(_.map { col =>
        if (col.name === collectionName)
          col.copy(books = col.books.filterNot(_.isbn === isbn))
        else col
      })
    } yield ()

  private def collectionOrError(collectionName: String): F[Collection] =
    for {
      maybeCollection <- collection(collectionName)
      retrievedCollection <- MonadError[F, Throwable].fromOption(
        maybeCollection,
        CollectionDoesNotExistError(collectionName)
      )
    } yield retrievedCollection
}
