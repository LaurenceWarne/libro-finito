package fin

import caliban.{CalibanError, GraphQLInterpreter, Http4sAdapter}
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import cats.effect.std.Dispatcher
import fs2.Stream
import org.http4s.server.Router
import org.http4s.server.middleware.Logger
import org.http4s.{HttpRoutes, Response, StaticFile}

object Routes {

  def routes(
      interpreter: GraphQLInterpreter[Any, CalibanError]
  )(implicit
      runtime: zio.Runtime[Any],
      dispatcher: Dispatcher[IO]
  ): HttpRoutes[IO] = {
    val serviceRoutes =
      Http4sAdapter.makeHttpServiceF[IO, Any, CalibanError](interpreter)
    val loggedRoutes =
      Logger.httpRoutes(logHeaders = true, logBody = true)(serviceRoutes)
    Router[IO](
      "/version" -> Kleisli.liftF(
        OptionT.pure[IO](
          Response[IO](body = Stream.emits(BuildInfo.version.getBytes("UTF-8")))
        )
      ),
      "/api/graphql" -> loggedRoutes,
      "/graphiql" -> Kleisli.liftF(
        StaticFile
          .fromResource("/graphql-playground.html", None)
      )
    )
  }
}
