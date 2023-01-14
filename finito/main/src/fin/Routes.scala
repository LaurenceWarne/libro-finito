package fin

import scala.concurrent.duration._

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
import org.http4s.server.middleware.ResponseTiming
import org.http4s.client.Client
import org.typelevel.ci._

import fin.Types._
import fin.implicits._

object Routes {

  type Env = zio.Clock

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
    finitoLoggingMiddleware[F](debug, ResponseTiming[F](app))
  }

  private val queryJson = """{
  "operationName": null,
  "query": "{collection(name: \"My Books\", booksPagination: {first: 5, after: 0}) {name books {title}}}",
  "variables": {}}"""

  def keepFresh[F[_]: Async](
      client: Client[F],
      timer: Temporal[F],
      port: Int,
      host: String
  ): F[Unit] = {
    val uriStr = show"http://$host:$port/api/graphql"
    val headers =
      Headers(("Accept", "application/json"), ("X-Client-Id", "finito"))
    val body = fs2.Stream.emits(queryJson.getBytes("UTF-8"))
    val result = for {
      uri <- Concurrent[F].fromEither(Uri.fromString(uriStr))
      request = Request[F](Method.POST, uri, headers = headers, body = body)
      _ <- client.expect[String](request)
    } yield ()
    (result >> timer.sleep(1.minutes)).foreverM
  }

  private def finitoLoggingMiddleware[F[_]: Async](
      debug: Boolean,
      app: HttpApp[F]
  ): HttpApp[F] = {
    val fromFinito = (r: Request[F]) =>
      r.headers.get(ci"X-Client-Id").exists(nel => nel.head.value === "finito")
    conditionalServerResponseLogger(
      logHeadersWhen = (r: Request[F]) => !fromFinito(r) || debug,
      logBodyWhen = Function.const(debug) _
    )(app)
  }

  // https://github.com/http4s/http4s/issues/4528
  private def conditionalServerResponseLogger[F[_]: Async](
      logHeadersWhen: Request[F] => Boolean,
      logBodyWhen: Request[F] => Boolean
  )(app: HttpApp[F]): HttpApp[F] = {
    val logger                       = org.log4s.getLogger
    val logAction: String => F[Unit] = s => Async[F].delay(logger.info(s))

    Kleisli { req =>
      app(req).flatTap { res =>
        Async[F].whenA(logHeadersWhen(req)) {
          org.http4s.internal.Logger
            .logMessage[F](res)(logHeadersWhen(req), logBodyWhen(req))(
              logAction
            )
        }
      }
    }
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
