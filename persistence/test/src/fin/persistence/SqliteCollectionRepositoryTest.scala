package fin.persistence

import weaver._
import doobie.implicits._
import doobie.util.transactor.Transactor
import cats.effect.IO
import cats.implicits._
import cats.effect.Resource
import doobie.util.fragment.Fragment

object SqliteCollectionRepositoryTest extends IOSuite {

  // See https://sqlite.org/forum/draft2/forumpost/cf7424bc7b4f37e1
  // or https://www.sqlite.org/inmemorydb.html
  val (uri, user, password) = ("jdbc:sqlite::memory:?cache=shared", "", "")
  val xa = Transactor.fromDriverManager[IO](
    "org.sqlite.JDBC",
    uri,
    user,
    password
  )

  val repo = SqliteCollectionRepository(xa)
  // FIXME According to the link: 'The database is automatically
  // deleted and memory is reclaimed when the last connection to the database
  // closes.' but this doesn't seem to be the case
  val dropTables =
    for {
      tables <-
        fr"SELECT name FROM sqlite_master WHERE type='table';"
          .query[String]
          .to[List]
          .transact(xa)
      _ <- tables.traverse(name => {
        fr"DROP TABLE IF EXISTS ${Fragment.const(name)};".update.run
          .transact(xa)
      })
    } yield ()

  override type Res = Unit
  override def sharedResource: Resource[IO, Res] =
    Resource.make(
      FlywaySetup.init[IO](
        uri,
        user,
        password
      )
    )(_ => dropTables)

  test("createCollection creates collection with correct attributes") { _ =>
    val name = "my collection"
    for {
      collection <- repo.createCollection(name)
    } yield expect(collection.name == name)
  }

  test("collections retrieves all created collections") { _ =>
    for {
      c1                   <- repo.createCollection("collection1")
      c2                   <- repo.createCollection("collection2")
      c3                   <- repo.createCollection("collection3")
      retrievedCollections <- repo.collections
    } yield expect(retrievedCollections.toSet == Set(c1, c2, c3))
  }

}
