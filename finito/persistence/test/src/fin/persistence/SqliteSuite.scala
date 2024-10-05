package fin.persistence

import cats.effect.{IO, Resource}
import cats.implicits._
import doobie._
import doobie.implicits._
import fs2.io.file._
import weaver._

trait SqliteSuite extends IOSuite {

  val dbFile                = Path(".").normalize.absolute / "tmp.db"
  val (uri, user, password) = (show"jdbc:sqlite:$dbFile", "", "")

  def transactor: Resource[IO, Transactor[IO]] =
    TransactorSetup.sqliteTransactor[IO](uri)

  // We can't use the in memory db since that is killed whenever no connections
  // exist
  val deleteDb: IO[Unit] = Files[IO].delete(dbFile)

  override type Res = Transactor[IO]
  override def sharedResource: Resource[IO, Transactor[IO]] =
    Resource.make(
      FlywaySetup.init[IO](
        uri,
        user,
        password
      )
    )(_ => deleteDb) *> transactor

  // See https://www.sqlite.org/faq.html#q5 of why generally it's a bad idea
  // to run sqlite writes in parallel
  override def maxParallelism = 1

  def testDoobie(name: String)(block: => ConnectionIO[Expectations]) =
    test(name)(xa => block.transact(xa))
}
