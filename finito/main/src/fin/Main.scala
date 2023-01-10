package fin

import _root_.cats.data.Kleisli
import _root_.cats.effect._
import _root_.cats.effect.std.Dispatcher
import _root_.cats.implicits._
import caseapp._
import caseapp.cats._
import com.ovoenergy.natchez.extras.doobie.TracedTransactor
import doobie._
import io.jaegertracing.Configuration
import natchez.jaeger.Jaeger
import natchez.{EntryPoint, Span}
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.client.Client
import org.typelevel.ci._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import zio.Runtime

import fin.config._
import fin.persistence._
import org.http4s.HttpRoutes

object Main extends IOCaseApp[CliOptions] {

  implicit val zioRuntime =
    Runtime.default.withEnvironment(
      zio.ZEnvironment(zio.Clock.ClockLive, zio.Console.ConsoleLive)
    )
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger

  def run(options: CliOptions, arg: RemainingArgs): IO[ExitCode] = {
    val server = serviceResources(options).use { serviceResources =>
      implicit val dispatcherEv = serviceResources.dispatcher
      val config                = serviceResources.config
      val ep                    = serviceResources.ep
      for {
        _ <- FlywaySetup.init[IO](
          config.databaseUri,
          config.databaseUser,
          config.databasePassword
        )
        _ <- logger.info(
          show"Starting finito server version ${BuildInfo.version}"
        )
        _           <- logger.debug("Creating services...")
        services    <- Services[IO](serviceResources)
        _           <- logger.debug("Bootstrapping caliban...")
        interpreter <- CalibanSetup.interpreter[IO](services, ep)

        debug <- IO(sys.env.get("LOG_LEVEL").exists(CIString(_) === ci"DEBUG"))
        _     <- logger.debug("Starting http4s server...")
        _     <- CalibanSetup.keepFresh[IO](interpreter, Temporal[IO]).start
        server <-
          BlazeServerBuilder[IO]
            .withBanner(Seq(Banner.value))
            .bindHttp(config.port, config.host)
            .withHttpApp(
              Routes.routes[IO](interpreter, ep, HttpRoutes.empty[IO], debug)
            )
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
      tracedTransactor =
        TracedTransactor(service = "finito-db", transactor = transactor)
      dispatcher <- Dispatcher[IO]
      ep <- Jaeger.entryPoint[IO]("finito")({ c =>
        IO {
          c.withSampler(
            (new Configuration.SamplerConfiguration)
              .withType("const")
              .withParam(1)
          ).withReporter(Configuration.ReporterConfiguration.fromEnv)
            .getTracer
        }
      })
    } yield ServiceResources(client, config, tracedTransactor, dispatcher, ep)
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
    transactor: Transactor[Kleisli[F, Span[F], *]],
    dispatcher: Dispatcher[F],
    ep: EntryPoint[F]
)
