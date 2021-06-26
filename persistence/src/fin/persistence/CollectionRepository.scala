package fin.persistence

import java.util.UUID

import fin.Types._

trait CollectionRepository[F[_]] {
  def collections: F[Unit]
  def createCollection(name: String): F[UUID]
  def collection(id: UUID): F[Book]
  def deleteCollection(name: String): F[Unit]
  def changeCollectionName(id: UUID, name: String): F[Unit]
  def addBookToCollection(collection: UUID, book: Book): F[Unit]
}
