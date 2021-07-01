package fin.persistence

import java.io.File

import cats.effect.{IO, Resource}
import cats.implicits._
import doobie.util.transactor.Transactor
import weaver._

object SqliteCollectionRepositoryTest extends IOSuite {

  val filename              = "tmp.db"
  val (uri, user, password) = (show"jdbc:sqlite:$filename", "", "")
  val xa = Transactor.fromDriverManager[IO](
    "org.sqlite.JDBC",
    uri,
    user,
    password
  )

  val repo = SqliteCollectionRepository(xa)
  // We can't use the in memory db since that is killed whenever no connections
  // exist
  val deleteDb: IO[Unit] = for {
    file <- IO(new File(filename))
    _    <- IO(file.delete())
  } yield ()

  override type Res = Unit
  override def sharedResource: Resource[IO, Res] =
    Resource.make(
      FlywaySetup.init[IO](
        uri,
        user,
        password
      )
    )(_ => deleteDb)

  test("createCollection creates collection with correct attributes") { _ =>
    val name = "my collection"
    for {
      collection <- repo.createCollection(name)
    } yield expect(collection.name == name)
  }

  test("collections retrieves created collections") { _ =>
    for {
      c1                   <- repo.createCollection("collection1")
      c2                   <- repo.createCollection("collection2")
      c3                   <- repo.createCollection("collection3")
      retrievedCollections <- repo.collections
    } yield expect(Set(c1, c2, c3).subsetOf(retrievedCollections.toSet))
  }

}
