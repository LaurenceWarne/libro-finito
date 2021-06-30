package fin.persistence

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import java.util.UUID
import fin.Types._

class SqliteCollectionRepository[F[_]: Sync](xa: Transactor[F])
    extends CollectionRepository[F] {
  def addBookToCollection(collection: UUID, book: Book): F[Collection] =
    ???

  def changeCollectionName(id: UUID, name: String): F[Collection] = ???

  def collection(id: UUID): F[Collection] = ???

  def collections: F[List[Collection]] = ???

  def createCollection(name: String): F[Collection] = {
    for {
      id <- Sync[F].delay(UUID.randomUUID())
      _ <-
        fr"INSERT INTO collections VALUES (${id.toString}, $name)".update.run
          .transact(xa)
    } yield Collection(id, name, List.empty[Book])
  }

  def deleteCollection(name: String): F[Unit] = ???
}
