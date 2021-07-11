package fin

import scala.concurrent.ExecutionContext.global

import better.files._
import cats.effect._
import cats.implicits._
import doobie._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import pureconfig._
import zio.Runtime

import fin.persistence.{DbProperties, FlywaySetup, SqliteCollectionRepository}
import fin.service._

import File._
import ServiceConfig._

object Main extends IOApp {

  implicit val runtime = Runtime.default

  override def run(args: List[String]): IO[ExitCode] = {
    val server =
      (BlazeClientBuilder[IO](global).resource, Blocker[IO]).tupled.use {
        case (client, blocker) =>
          for {
            configDirectory <- configDirectory
            _               <- initializeConfigLocation(configDirectory)
            confResponse =
              ConfigSource
                .file((configDirectory / "service.conf").toString)
                .optional
                .withFallback(ServiceConfig.default(configDirectory.toString))
                .load[ServiceConfig]
                .leftMap(err => new Exception(err.toString))
            conf <- IO.fromEither(confResponse)
            (uri, user, password) =
              (show"jdbc:sqlite:${conf.databasePath}", "", "")
            xa = Transactor.fromDriverManager[IO](
              "org.sqlite.JDBC",
              uri,
              DbProperties.properties
            )
            _ <- FlywaySetup.init[IO](uri, user, password)
            clock          = Clock[IO]
            collectionRepo = SqliteCollectionRepository[IO](xa, clock)
            implicit0(logger: Logger[IO]) <- Slf4jLogger.create[IO]
            _                             <- logger.debug("Creating services...")
            bookInfoService   = GoogleBookInfoService[IO](client)
            collectionService = CollectionServiceImpl(collectionRepo)
            _ <- logger.debug("Bootstrapping caliban...")
            interpreter <-
              CalibanSetup.interpreter[IO](bookInfoService, collectionService)
            server <-
              BlazeServerBuilder[IO](global)
                .withBanner(Seq(Banner.value))
                .bindHttp(conf.port, "localhost")
                .withHttpApp(Routes.routes(interpreter, blocker).orNotFound)
                .serve
                .compile
                .drain
          } yield server
      }
    server.as(ExitCode.Success)
  }

  def initializeConfigLocation(configDirectory: File): IO[Unit] =
    IO(configDirectory.createDirectoryIfNotExists())

  def configDirectory: IO[File] = IO(home / ".config" / "libro-finito")
}

object Banner {
  val value: String = """
 _________________
< Server started! >
 -----------------
\                             .       .
 \                           / `.   .' " 
  \                  .---.  <    > <    >  .---.
   \                 |    \  \ - ~ ~ - /  /    |
         _____          ..-~             ~-..-~
        |     |   \~~~\.'                    `./~~~/
       ---------   \__/                        \__/
      .'  O    \     /               /       \  " 
     (_____,    `._.'               |         }  \/~~~/
      `----.          /       }     |        /    \__/
            `-.      |       /      |       /      `. ,~~|
                ~-.__|      /_ - ~ ^|      /- _      `..-'   
                     |     /        |     /     ~-.     `-. _  _  _
                     |_____|        |_____|         ~ - . _ _ _ _ _>
"""
}
