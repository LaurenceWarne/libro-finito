package fin.persistence

import java.util.UUID

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor

import fin.Types._

class SqliteCollectionRepository[F[_]: Sync] private (xa: Transactor[F])
    extends CollectionRepository[F] {
  def addBookToCollection(collection: String, book: Book): F[Collection] =
    ???

  def changeCollectionName(id: String, name: String): F[Collection] = ???

  def collection(id: String): F[Collection] = ???

  def collections: F[List[Collection]] =
    fr"SELECT id, name FROM collections"
      .query[(String, String)]
      .to[List]
      .transact(xa)
      .nested
      .map(tup => Collection(tup._1, tup._2, Nil))
      .value

  def createCollection(name: String): F[Collection] = {
    for {
      id <- Sync[F].delay(UUID.randomUUID().toString)
      _ <-
        fr"INSERT INTO collections VALUES ($id, $name)".update.run
          .transact(xa)
    } yield Collection(id, name, List.empty[Book])
  }

  def deleteCollection(name: String): F[Unit] = ???
}

object SqliteCollectionRepository {

  def apply[F[_]: Sync](xa: Transactor[F]) =
    new SqliteCollectionRepository[F](xa)
}
