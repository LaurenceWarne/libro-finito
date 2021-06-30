package fin.persistence

import fin.Types._

trait CollectionRepository[F[_]] {
  def collections: F[List[Collection]]
  def createCollection(name: String): F[Collection]
  def collection(id: String): F[Collection]
  def deleteCollection(name: String): F[Unit]
  def changeCollectionName(id: String, name: String): F[Collection]
  def addBookToCollection(collection: String, book: Book): F[Collection]
}
