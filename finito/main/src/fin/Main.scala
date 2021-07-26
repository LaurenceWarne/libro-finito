package fin

import scala.concurrent.ExecutionContext.global

import cats.effect._
import cats.implicits._
import doobie._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import zio.Runtime

import fin.config._
import fin.persistence._
import fin.service._

object Main extends IOApp {

  implicit val runtime = Runtime.default

  override def run(args: List[String]): IO[ExitCode] = {
    val server =
      (BlazeClientBuilder[IO](global).resource, Blocker[IO]).tupled.use {
        case (client, blocker) =>
          for {
            config <- Config.get[IO]
            uri = show"jdbc:sqlite:${config.databasePath}"
            xa = Transactor.fromDriverManager[IO](
              config.databaseDriver,
              uri,
              DbProperties.properties
            )
            _ <- FlywaySetup.init[IO](
              uri,
              config.databaseUser,
              config.databasePassword
            )
            clock          = Clock[IO]
            collectionRepo = SqliteCollectionRepository[IO](xa, clock)
            bookRepo       = SqliteBookRepository[IO](xa)
            implicit0(logger: Logger[IO]) <- Slf4jLogger.create[IO]
            _                             <- logger.debug("Creating services...")
            bookInfoService      = GoogleBookInfoService[IO](client)
            collectionService    = CollectionServiceImpl(collectionRepo)
            bookManagmentService = BookManagementServiceImpl(bookRepo, clock)
            wrappedCollectionService <- SpecialCollectionSetup.setup(
              collectionService,
              config.defaultCollection,
              config.specialCollections
            )
            _ <- logger.debug("Bootstrapping caliban...")
            interpreter <- CalibanSetup.interpreter[IO](
              bookInfoService,
              bookManagmentService,
              wrappedCollectionService
            )
            server <-
              BlazeServerBuilder[IO](global)
                .withBanner(Seq(Banner.value))
                .bindHttp(config.port, "localhost")
                .withHttpApp(Routes.routes(interpreter, blocker).orNotFound)
                .serve
                .compile
                .drain
          } yield server
      }
    server.as(ExitCode.Success)
  }
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