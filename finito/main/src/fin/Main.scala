package fin

import scala.concurrent.ExecutionContext.global

import _root_.cats.arrow.FunctionK
import _root_.cats.effect._
import _root_.cats.implicits._
import caseapp._
import caseapp.cats._
import doobie._
import doobie.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.GZip
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import zio.Runtime

import fin.config._
import fin.persistence._
import fin.service.book._
import fin.service.collection._

object Main extends IOCaseApp[CliOptions] {
  implicit val runtime = Runtime.default

  def run(options: CliOptions, arg: RemainingArgs): IO[ExitCode] = {
    val server =
      (
        BlazeClientBuilder[IO](global).resource,
        Blocker[IO],
        ExecutionContexts.fixedThreadPool[IO](4)
      ).tupled.use {
        case (client, blocker, ec) =>
          for {
            implicit0(logger: Logger[IO]) <- Slf4jLogger.create[IO]
            config                        <- Config.get[IO](options.config)
            uri = show"jdbc:sqlite:${config.databasePath}"
            xa = TransactorSetup.sqliteTransactor[IO](
              uri,
              ec,
              blocker
            )
            _ <- FlywaySetup.init[IO](
              uri,
              config.databaseUser,
              config.databasePassword
            )
            clock          = Clock[IO]
            collectionRepo = SqliteCollectionRepository
            bookRepo       = SqliteBookRepository
            _ <- logger.debug("Creating services...")
            bookInfoService  = GoogleBookInfoService[IO](GZip()(client))
            connectionIOToIO = λ[FunctionK[ConnectionIO, IO]](_.transact(xa))
            wrappedInfoService = BookInfoAugmentationService[IO, ConnectionIO](
              bookInfoService,
              bookRepo,
              connectionIOToIO
            )
            collectionService = CollectionServiceImpl[IO, ConnectionIO](
              collectionRepo,
              clock,
              connectionIOToIO
            )
            bookManagmentService = BookManagementServiceImpl[IO, ConnectionIO](
              bookRepo,
              clock,
              connectionIOToIO
            )
            (wrappedBookManagementService, wrappedCollectionService) <-
              SpecialCollectionSetup.setup[IO](
                collectionService,
                bookManagmentService,
                config.defaultCollection,
                config.specialCollections
              )
            _ <- logger.debug("Bootstrapping caliban...")
            interpreter <- CalibanSetup.interpreter[IO](
              wrappedInfoService,
              wrappedBookManagementService,
              wrappedCollectionService
            )
            server <-
              BlazeServerBuilder[IO](global)
                .withBanner(Seq(Banner.value))
                .bindHttp(config.port, config.host)
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
