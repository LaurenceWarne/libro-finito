package fin

import cats.effect._
import cats.implicits._
import fs2._
import fs2.io.file._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.config.{Config, ServiceConfig}

object ConfigTest extends SimpleIOSuite {

  implicit val logger = Slf4jLogger.getLogger[IO]
  val testDir         = Path("./out/conf-test").normalize.absolute

  override def maxParallelism = 1

  test("creates config directory if not exists") {
    for {
      _ <- Files[IO].deleteRecursively(testDir).recover {
        case _: NoSuchFileException => ()
      }
      _      <- Config(testDir.toString)
      exists <- Files[IO].exists(testDir)
      _      <- Files[IO].deleteIfExists(testDir)
    } yield expect(exists)
  }

  test("no error if config directory already exists") {
    for {
      _ <- Files[IO].deleteRecursively(testDir).recover {
        case _: NoSuchFileException => ()
      }
      _ <- Config(testDir.toString)
      _ <- Files[IO].deleteIfExists(testDir)
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
      _ <- Files[IO].createDirectories(testDir)
      _ <-
        Stream
          .emits(configContents.getBytes("UTF-8"))
          .through(Files[IO].writeAll(testDir / "service.conf"))
          .compile
          .drain
      conf <- Config(testDir.toString)
      _    <- Files[IO].deleteRecursively(testDir)
    } yield expect(
      ServiceConfig(
        ServiceConfig.defaultDatabasePath(testDir.toString),
        ServiceConfig.defaultDatabaseUser,
        ServiceConfig.defaultDatabasePassword,
        ServiceConfig.defaultHost,
        ServiceConfig.defaultPort,
        Some(ServiceConfig.defaultDefaultCollection),
        ServiceConfig.defaultSpecialCollections
      ) === conf
    )
  }
}
