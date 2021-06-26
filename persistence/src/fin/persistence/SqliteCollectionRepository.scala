package fin.persistence

class SqliteCollectionRepository[F[_]] extends CollectionRepository[F] {
  def addBookToCollection(
      collection: java.util.UUID,
      book: fin.Types.Book
  ): F[Unit]                                                          = ???
  def changeCollectionName(id: java.util.UUID, name: String): F[Unit] = ???
  def collection(id: java.util.UUID): F[fin.Types.Book]               = ???
  def collections: F[Unit]                                            = ???
  def createCollection(name: String): F[java.util.UUID]               = ???
  def deleteCollection(name: String): F[Unit]                         = ???
}
