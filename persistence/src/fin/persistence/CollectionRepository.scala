package fin.persistence

import fin.Types._

trait CollectionRepository[F[_]] {
  def collections: F[List[Collection]]
  def createCollection(name: String): F[Collection]
  def collection(name: String): F[Option[Collection]]
  def deleteCollection(name: String): F[Unit]
  def changeCollectionName(currentName: String, newName: String): F[Collection]
  def addBookToCollection(collectionName: String, book: Book): F[Collection]
  def removeBookFromCollection(
      collectionName: String,
      book: Book
  ): F[Unit]
}
