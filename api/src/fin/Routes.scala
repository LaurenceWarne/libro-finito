package fin

import caliban.{CalibanError, GraphQLInterpreter, Http4sAdapter}
import cats.data.{Kleisli, OptionT}
import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import fs2.{Stream, text}
import io.chrisdavenport.log4cats.Logger
import org.http4s.server.Router
import org.http4s.{HttpRoutes, Request, Response, StaticFile}

object Routes {

  private def loggingMiddleware(
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

  def routes(
      interpreter: GraphQLInterpreter[Any, CalibanError],
      blocker: Blocker
  )(implicit
      logger: Logger[IO],
      runtime: zio.Runtime[Any],
      cs: ContextShift[IO]
  ): HttpRoutes[IO] = {
    val serviceRoutes =
      Http4sAdapter.makeHttpServiceF[IO, CalibanError](interpreter)
    Router[IO](
      "/version" -> Kleisli.liftF(
        OptionT.pure[IO](
          Response[IO](body = Stream.emits(BuildInfo.version.getBytes("UTF-8")))
        )
      ),
      "/api/graphql" -> loggingMiddleware(serviceRoutes),
      "/graphiql" -> Kleisli.liftF(
        StaticFile
          .fromResource("/graphql-playground.html", blocker, None)
      )
    )
  }
}
