package fin

import scala.concurrent.ExecutionContext.global

import _root_.cats.arrow.FunctionK
import _root_.cats.effect._
import _root_.cats.effect.std.Dispatcher
import caseapp._
import caseapp.cats._
import doobie._
import doobie.implicits._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.client.middleware.GZip
import org.http4s.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import zio.Runtime

import fin.config._
import fin.persistence._
import fin.service.book._
import fin.service.collection._

object Main extends IOCaseApp[CliOptions] {

  implicit val zioRuntime         = Runtime.default
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger

  def resources(options: CliOptions) =
    for {
      client <- BlazeClientBuilder[IO](global).resource
      config <- Resource.eval(Config.get[IO](options.config))
      transactor <- ExecutionContexts.fixedThreadPool[IO](4).flatMap { ec =>
        TransactorSetup.sqliteTransactor[IO](
          config.databaseUri,
          ec
        )
      }
      dispatcher <- Dispatcher[IO]
    } yield (client, config, transactor, dispatcher)

  def run(options: CliOptions, arg: RemainingArgs): IO[ExitCode] = {
    val server = resources(options).use {
      case (client, config, transactor, dispatcher) =>
        implicit val dispatcherEv = dispatcher
        for {
          _ <- FlywaySetup.init[IO](
            config.databaseUri,
            config.databaseUser,
            config.databasePassword
          )
          clock          = Clock[IO]
          collectionRepo = SqliteCollectionRepository
          bookRepo       = SqliteBookRepository
          _ <- logger.debug("Creating services...")
          bookInfoService = GoogleBookInfoService[IO](GZip()(client))
          connectionIOToIO =
            λ[FunctionK[ConnectionIO, IO]](_.transact(transactor))
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
              .withHttpApp(Routes.routes(interpreter).orNotFound)
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
