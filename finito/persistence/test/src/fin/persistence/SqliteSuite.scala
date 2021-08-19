package fin.persistence

import javax.sql.DataSource

import better.files.Dsl._
import better.files._
import cats.Show
import cats.effect.{Blocker, IO, Resource}
import cats.implicits._
import doobie._
import doobie.implicits._
import org.sqlite.{SQLiteConfig, SQLiteDataSource}
import weaver._

trait SqliteSuite extends IOSuite {
  implicit val fileShow: Show[File] = Show.fromToString

  val dbFile                = pwd / "tmp.db"
  val (uri, user, password) = (show"jdbc:sqlite:$dbFile", "", "")

  def dataSource: DataSource = {
    val ds  = new SQLiteDataSource()
    val cfg = new SQLiteConfig(DbProperties.properties)
    ds.setConfig(cfg)
    ds.setUrl(uri)
    ds
  }

  def transactor: Resource[IO, Transactor[IO]] = {
    val ds = dataSource
    (Blocker[IO], ExecutionContexts.fixedThreadPool[IO](4)).tupled.map {
      case (blocker, ec) =>
        Transactor.fromDataSource[IO](ds, ec, blocker)
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
