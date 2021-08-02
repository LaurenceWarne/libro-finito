package fin.persistence

import better.files.Dsl._
import better.files._
import cats.Show
import cats.effect.{IO, Resource}
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import weaver._

trait SqliteSuite extends IOSuite {
  implicit val fileShow: Show[File] = Show.fromToString

  val dbFile                = pwd / "tmp.db"
  val (uri, user, password) = (show"jdbc:sqlite:$dbFile", "", "")
  val xa = Transactor.fromDriverManager[IO](
    "org.sqlite.JDBC",
    uri,
    DbProperties.properties
  )

  // We can't use the in memory db since that is killed whenever no connections
  // exist
  val deleteDb: IO[Unit] = IO(dbFile.delete())

  override type Res = Unit
  override def sharedResource: Resource[IO, Unit] =
    Resource.make(
      FlywaySetup.init[IO](
        uri,
        user,
        password
      )
    )(_ => deleteDb)

  // See https://www.sqlite.org/faq.html#q5 of why generally it's a bad idea
  // to run sqlite writes in parallel
  override def maxParallelism = 1

  def testDoobie(name: String)(block: => ConnectionIO[Expectations]) =
    test(name)(block.transact(xa))
}
