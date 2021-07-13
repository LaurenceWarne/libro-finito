package fin.persistence

import fin.Types._

trait CollectionRepository[F[_]] {
  def collections: F[List[Collection]]
  def createCollection(name: String): F[Unit]
  def collection(name: String): F[Option[Collection]]
  def deleteCollection(name: String): F[Unit]
  def updateCollection(currentName: String, newName: String): F[Unit]
  def addBookToCollection(collectionName: String, book: Book): F[Unit]
  def removeBookFromCollection(collectionName: String, isbn: String): F[Unit]
}
