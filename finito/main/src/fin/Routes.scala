package fin

import caliban.interop.cats.CatsInterop
import caliban.{CalibanError, GraphQLInterpreter, Http4sAdapter}
import cats.data.{Kleisli, OptionT}
import cats.effect.std.Dispatcher
import cats.effect.{Async, IO}
import cats.~>
import fs2.Stream
import org.http4s._
import org.http4s.server.Router
import org.http4s.server.middleware.{Logger, ResponseTiming}

object Routes {

  type Env = zio.clock.Clock with zio.blocking.Blocking

  def routes(
      interpreter: GraphQLInterpreter[Any, CalibanError],
      debug: Boolean
  )(implicit
      runtime: zio.Runtime[Env],
      dispatcher: Dispatcher[IO]
  ): HttpApp[IO] = {
    val serviceRoutes: HttpRoutes[IO] =
      wrapRoute[IO, Env](
        Http4sAdapter.makeHttpService[Any, CalibanError](interpreter)
      )
    val app = Router[IO](
      "/version" -> Kleisli.liftF(
        OptionT.pure[IO](
          Response[IO](body = Stream.emits(BuildInfo.version.getBytes("UTF-8")))
        )
      ),
      "/api/graphql" -> serviceRoutes,
      "/graphiql" -> Kleisli.liftF(
        StaticFile.fromResource("/graphql-playground.html", None)
      )
    ).orNotFound
    Logger.httpApp(logHeaders = true, logBody = debug)(ResponseTiming(app))
  }

  private def wrapRoute[F[_]: Async, R](
      route: HttpRoutes[zio.RIO[R, *]]
  )(implicit
      dispatcher: Dispatcher[F],
      runtime: zio.Runtime[R]
  ): HttpRoutes[F] = {
    val toF: zio.RIO[R, *] ~> F   = CatsInterop.toEffectK
    val toRIO: F ~> zio.RIO[R, *] = CatsInterop.fromEffectK
    val to: OptionT[zio.RIO[R, *], *] ~> OptionT[F, *] =
      new (OptionT[zio.RIO[R, *], *] ~> OptionT[F, *]) {
        def apply[A](fa: OptionT[zio.RIO[R, *], A]): OptionT[F, A] =
          fa.mapK(toF)
      }
    route
      .mapK(to)
      .dimap((req: Request[F]) => req.mapK(toRIO))(
        (res: Response[zio.RIO[R, *]]) => res.mapK(toF)
      )
  }
}
