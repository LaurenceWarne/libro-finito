package fin

import scala.collection.immutable

import cats.effect._
import cats.effect.std.Env
import cats.implicits._
import fs2._
import fs2.io.file._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.config.{Config, ServiceConfig}

object ConfigTest extends SimpleIOSuite {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  val testDir                     = Path("./out/conf-test").normalize.absolute
  val configPath                  = testDir / "libro-finito"

  override def maxParallelism = 1

  val testEnv = new Env[IO] {
    private val mp                                     = Map("XDG_CONFIG_HOME" -> testDir.toString)
    override def get(name: String): IO[Option[String]] = IO.pure(mp.get(name))
    override def entries: IO[immutable.Iterable[(String, String)]] =
      IO.pure(mp.toList)
  }

  test("creates config directory if not exists") {
    for {
      _ <- Files[IO].deleteRecursively(testDir).recover {
        case _: NoSuchFileException => ()
      }
      _      <- Config(testEnv)
      exists <- Files[IO].exists(testDir)
      _      <- Files[IO].deleteRecursively(testDir)
    } yield expect(exists)
  }

  test("no error if config directory already exists") {
    for {
      _ <- Files[IO].deleteRecursively(testDir).recover {
        case _: NoSuchFileException => ()
      }
      _ <- Config(testEnv)
      _ <- Files[IO].deleteRecursively(testDir)
    } yield success
  }

  test("config file respected if it exists") {
    val port              = 1337
    val defaultCollection = "Bookies"
    val configContents =
      show"""{
          |  port = $port,
          |  default-collection = $defaultCollection
          |}""".stripMargin
    for {
      _ <- Files[IO].createDirectories(configPath)
      _ <-
        Stream
          .emits(configContents.getBytes("UTF-8"))
          .through(
            Files[IO].writeAll(configPath / "service.conf")
          )
          .compile
          .drain
      conf <- Config(testEnv)
      _    <- Files[IO].deleteRecursively(testDir)
    } yield expect(
      ServiceConfig(
        ServiceConfig.defaultDatabasePath(configPath.toString),
        ServiceConfig.defaultDatabaseUser,
        ServiceConfig.defaultDatabasePassword,
        ServiceConfig.defaultHost,
        port,
        Some(defaultCollection),
        ServiceConfig.defaultSpecialCollections
      ) === conf
    )
  }
}
