package fin

import scala.concurrent.ExecutionContext.global

import caliban.interop.cats.implicits._
import caliban.{CalibanError, GraphQL, Http4sAdapter, RootResolver}
import cats.data.{Kleisli, OptionT}
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import fs2.text
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.{HttpRoutes, Request, StaticFile}
import zio.Runtime

import fin.service.{GoogleBookInfoService, Queries}

object Main extends IOApp {

  implicit val runtime = Runtime.default

  def loggingMiddleware(
      service: HttpRoutes[IO]
  )(implicit logger: Logger[IO]): HttpRoutes[IO] =
    Kleisli { req: Request[IO] =>
      OptionT.liftF(
        logger.info(
          "REQUEST:  " + req + req.body
            .through(text.utf8Decode)
            .compile
            .toList
            .unsafeRunSync()
        )
      ) *>
        service(req)
          .onError(e => OptionT.liftF(logger.error("ERROR:    " + e)))
          .flatMap { response =>
            OptionT.liftF(logger.info("RESPONSE:   " + response)) *>
              OptionT.liftF(IO(response))
          }
    }

  override def run(args: List[String]): IO[ExitCode] = {
    val server =
      (BlazeClientBuilder[IO](global).resource, Blocker[IO]).tupled.use {
        case (client, blocker) =>
          for {
            implicit0(logger: Logger[IO]) <- Slf4jLogger.create[IO]
            bookAPI = GoogleBookInfoService[IO](client)
            queries = Queries[IO](bookArgs => bookAPI.search(bookArgs))
            api     = GraphQL.graphQL(RootResolver(queries))
            interpreter <- api.interpreterAsync[IO]
            routes: HttpRoutes[IO] =
              Http4sAdapter.makeHttpServiceF[IO, CalibanError](interpreter)
            server <-
              BlazeServerBuilder[IO](global)
                .bindHttp(8080, "localhost")
                .withHttpApp(
                  Router[IO](
                    "/api/graphql" -> loggingMiddleware(routes),
                    "/graphiql" -> Kleisli.liftF(
                      StaticFile
                        .fromResource("/graphql-playground.html", blocker, None)
                    )
                  ).orNotFound
                )
                .serve
                .compile
                .drain
          } yield server
      }
    server.as(ExitCode.Success)
  }
}
