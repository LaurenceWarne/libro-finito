package fin

import caliban.execution.QueryExecution
import caliban.{CalibanError, GraphQLInterpreter, Http4sAdapter}
import cats.data.{Kleisli, OptionT}
import cats.effect._
import fs2.Stream
import org.http4s._
import org.http4s.server.Router
import org.http4s.server.middleware.{Logger, ResponseTiming}

object Routes {

  type Env = zio.clock.Clock with zio.blocking.Blocking

  def routes[F[_]: Async](
      interpreter: GraphQLInterpreter[Any, CalibanError],
      debug: Boolean
  )(implicit
      runtime: zio.Runtime[Env]
  ): HttpApp[F] = {
    val serviceRoutes: HttpRoutes[F] =
      Http4sAdapter.makeHttpServiceF[F, Any, CalibanError](
        interpreter,
        skipValidation = true,
        queryExecution = QueryExecution.Sequential
      )
    val app = Router[F](
      "/version" -> Kleisli.liftF(
        OptionT.pure[F](
          Response[F](body = Stream.emits(BuildInfo.version.getBytes("UTF-8")))
        )
      ),
      "/api/graphql" -> serviceRoutes,
      "/graphiql" -> Kleisli.liftF(
        StaticFile.fromResource("/graphql-playground.html", None)
      )
    ).orNotFound
    Logger.httpApp(logHeaders = true, logBody = debug)(ResponseTiming(app))
  }
}
