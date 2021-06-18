package fin.poc

import cats.effect.IOApp
import cats.effect.{ExitCode, IO}
import cats.implicits._
import caliban.RootResolver
import caliban.Http4sAdapter
import caliban.interop.cats.implicits._
import zio.Runtime
import caliban.GraphQLInterpreter
import caliban.GraphQL
import caliban.CalibanError
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import scala.concurrent.ExecutionContext.global
import org.http4s.server.Router
import cats.data.Kleisli
import org.http4s.StaticFile
import cats.effect.Blocker
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.Header
import org.http4s.Request
import org.http4s.Status
import cats.data.OptionT
import org.http4s.EntityDecoder
import fs2.text

import fin.api.{GoogleBookAPI, Queries}

object Main extends IOApp {

  implicit val runtime = Runtime.default

  def loggingMiddleware(service: HttpRoutes[IO]): HttpRoutes[IO] =
    Kleisli { req: Request[IO] =>
      OptionT.liftF(
        IO(
          println(
            "REQUEST:  " + req + req.body
              .through(text.utf8Decode)
              .compile
              .toList
              .unsafeRunSync()
          )
        )
      ) *>
        service(req)
          .onError(e => OptionT.liftF(IO(println("ERROR:    " + e))))
          .flatMap { response =>
            OptionT.liftF(IO(println("RESPONSE:   " + response))) *>
              OptionT.liftF(IO(response))
          }
    }

  override def run(args: List[String]): IO[ExitCode] = {
    val server =
      (BlazeClientBuilder[IO](global).resource, Blocker[IO]).tupled.use {
        case (client, blocker) =>
          val bookAPI = GoogleBookAPI[IO](client)
          val queries = Queries[IO](bookArgs => bookAPI.search(bookArgs))
          val api = GraphQL.graphQL(RootResolver(queries))
          for {
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
