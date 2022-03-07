package fin

import _root_.cats.effect._
import _root_.cats.effect.std.Dispatcher
import _root_.cats.implicits._
import caliban.interop.cats.implicits._
import caseapp._
import caseapp.cats._
import doobie._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.client.Client
import org.typelevel.ci._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import zio.Runtime

import fin.config._
import fin.persistence._

object Main extends IOCaseApp[CliOptions] {

  implicit val zioRuntime         = Runtime.default
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger

  def run(options: CliOptions, arg: RemainingArgs): IO[ExitCode] = {
    val server = serviceResources(options).use { serviceResources =>
      implicit val dispatcherEv = serviceResources.dispatcher
      val config                = serviceResources.config
      for {
        _ <- FlywaySetup.init[IO](
          config.databaseUri,
          config.databaseUser,
          config.databasePassword
        )
        _           <- logger.debug("Creating services...")
        services    <- Services[IO](serviceResources)
        _           <- logger.debug("Bootstrapping caliban...")
        interpreter <- CalibanSetup.interpreter[IO](services)
        _           <- interpreter.executeAsync[IO](CalibanSetup.introspectionQuery)
        debug       <- IO(sys.env.get("LOG_LEVEL").exists(CIString(_) === ci"DEBUG"))
        _           <- logger.debug("Starting http4s server...")
        server <-
          BlazeServerBuilder[IO]
            .withBanner(Seq(Banner.value))
            .bindHttp(config.port, config.host)
            .withHttpApp(Routes.routes[IO](interpreter, debug))
            .serve
            .compile
            .drain
      } yield server
    }
    server.as(ExitCode.Success)
  }

  private def serviceResources(
      options: CliOptions
  ): Resource[IO, ServiceResources[IO]] =
    for {
      client <- BlazeClientBuilder[IO].resource
      config <- Resource.eval(Config[IO](options.config))
      transactor <- ExecutionContexts.fixedThreadPool[IO](4).flatMap { ec =>
        TransactorSetup.sqliteTransactor[IO](
          config.databaseUri,
          ec
        )
      }
      dispatcher <- Dispatcher[IO]
    } yield ServiceResources(client, config, transactor, dispatcher)
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

final case class ServiceResources[F[_]](
    client: Client[F],
    config: ServiceConfig,
    transactor: Transactor[F],
    dispatcher: Dispatcher[F]
)
