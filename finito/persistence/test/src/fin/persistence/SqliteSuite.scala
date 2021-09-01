package fin.persistence

import better.files.Dsl._
import better.files._
import cats.Show
import cats.effect.{IO, Resource}
import cats.implicits._
import doobie._
import doobie.implicits._
import weaver._

trait SqliteSuite extends IOSuite {
  implicit val fileShow: Show[File] = Show.fromToString

  val dbFile                = pwd / "tmp.db"
  val (uri, user, password) = (show"jdbc:sqlite:$dbFile", "", "")

  def transactor: Resource[IO, Transactor[IO]] = {
    ExecutionContexts.fixedThreadPool[IO](4).flatMap { ec =>
      TransactorSetup.sqliteTransactor[IO](
        uri,
        ec
      )
    }
  }

  // We can't use the in memory db since that is killed whenever no connections
  // exist
  val deleteDb: IO[Unit] = IO(dbFile.delete())

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
