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

import fin.persistence.{DbProperties, FlywaySetup, SqliteCollectionRepository}
import fin.service.{CollectionServiceImpl, GoogleBookInfoService}

object Main extends IOApp {

  implicit val runtime = Runtime.default

  override def run(args: List[String]): IO[ExitCode] = {
    val server =
      (BlazeClientBuilder[IO](global).resource, Blocker[IO]).tupled.use {
        case (client, blocker) =>
          val (uri, user, password) = ("jdbc:sqlite:test.db", "", "")
          val xa = Transactor.fromDriverManager[IO](
            "org.sqlite.JDBC",
            uri,
            DbProperties.properties
          )
          for {
            _ <- FlywaySetup.init[IO](uri, user, password)
            clock          = Clock[IO]
            collectionRepo = SqliteCollectionRepository[IO](xa, clock)
            implicit0(logger: Logger[IO]) <- Slf4jLogger.create[IO]
            bookInfoService   = GoogleBookInfoService[IO](client)
            collectionService = CollectionServiceImpl(collectionRepo)
            interpreter <-
              CalibanSetup.interpreter(bookInfoService, collectionService)
            server <-
              BlazeServerBuilder[IO](global)
                .bindHttp(8080, "localhost")
                .withHttpApp(Routes.routes(interpreter, blocker).orNotFound)
                .serve
                .compile
                .drain
            _ <- logger.info("""
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
""")
          } yield server
      }
    server.as(ExitCode.Success)
  }
}
