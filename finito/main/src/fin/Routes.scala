package fin

import caliban.execution.QueryExecution
import caliban.{CalibanError, GraphQLInterpreter, Http4sAdapter}
import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits._
import fs2.Stream
import io.circe.literal._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.middleware.{Logger, ResponseTiming}

import fin.Types._
import fin.implicits._

object Routes {

  type Env = zio.clock.Clock with zio.blocking.Blocking

  def routes[F[_]: Async](
      interpreter: GraphQLInterpreter[Any, CalibanError],
      restApiRoutes: HttpRoutes[F],
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
      "/api"         -> restApiRoutes,
      "/graphiql" -> Kleisli.liftF(
        StaticFile.fromResource("/graphql-playground.html", None)
      )
    ).orNotFound
    Logger.httpApp(logHeaders = true, logBody = debug)(ResponseTiming(app))
  }
}

object HTTPService {

  object CollectionNameMatcher
      extends QueryParamDecoderMatcher[String]("collection-name")

  def routes[F[_]: Async](services: Services[F]): HttpRoutes[F] = {
    errorMiddleware[F](raw_routes[F](services))
  }

  private def errorMiddleware[F[_]: Async](
      service: HttpRoutes[F]
  ): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    Kleisli { req: Request[F] =>
      service(req).handleErrorWith { e =>
        OptionT.liftF(
          Ok(json"""{"data": null, "errors": [{"message": ${e.getMessage}}]}""")
        )
      }
    }

  }

  private def raw_routes[F[_]: Async](services: Services[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "collection" :? CollectionNameMatcher(
            collectionName
          ) =>
        services.collectionService
          .collection(
            QueriesCollectionArgs(collectionName, None)
          )
          .flatMap(c => Ok(c.asJson))
    }
  }
}
