package fin.persistence

import java.util.UUID

import fin.Types._

trait CollectionRepository[F[_]] {
  def collections: F[List[Collection]]
  def createCollection(name: String): F[Collection]
  def collection(id: UUID): F[Collection]
  def deleteCollection(name: String): F[Unit]
  def changeCollectionName(id: UUID, name: String): F[Collection]
  def addBookToCollection(collection: UUID, book: Book): F[Collection]
}
