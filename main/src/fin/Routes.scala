package fin

import caliban.{CalibanError, GraphQLInterpreter, Http4sAdapter}
import cats.data.{Kleisli, OptionT}
import cats.effect.{Blocker, Clock, ContextShift, IO}
import fs2.Stream
import org.http4s.server.Router
import org.http4s.{HttpRoutes, Response, StaticFile}
import org.http4s.server.middleware.{Logger, Metrics}
import org.http4s.metrics.dropwizard.Dropwizard
import com.codahale.metrics.SharedMetricRegistries

object Routes {

  def routes(
      interpreter: GraphQLInterpreter[Any, CalibanError],
      blocker: Blocker
  )(implicit
      runtime: zio.Runtime[Any],
      cs: ContextShift[IO],
      clock: Clock[IO]
  ): HttpRoutes[IO] = {
    val serviceRoutes =
      Http4sAdapter.makeHttpServiceF[IO, CalibanError](interpreter)
    val loggedRoutes =
      Logger.httpRoutes(logHeaders = true, logBody = true)(serviceRoutes)
    val registry = SharedMetricRegistries.getOrCreate("default")
    val meteredRoutes =
      Metrics[IO](Dropwizard(registry, "server"))(loggedRoutes)
    Router[IO](
      "/version" -> Kleisli.liftF(
        OptionT.pure[IO](
          Response[IO](body = Stream.emits(BuildInfo.version.getBytes("UTF-8")))
        )
      ),
      "/api/graphql" -> meteredRoutes,
      "/graphiql" -> Kleisli.liftF(
        StaticFile
          .fromResource("/graphql-playground.html", blocker, None)
      )
    )
  }
}
