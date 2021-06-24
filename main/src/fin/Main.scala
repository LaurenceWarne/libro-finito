package fin

import scala.concurrent.ExecutionContext.global

import caliban.interop.cats.implicits._
import caliban.{GraphQL, RootResolver}
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import zio.Runtime

import fin.service.{GoogleBookInfoService, Queries}

object Main extends IOApp {

  implicit val runtime = Runtime.default

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
            _ = println("Hi?????")
            server <-
              BlazeServerBuilder[IO](global)
                .bindHttp(8080, "localhost")
                .withHttpApp(Routes.routes(interpreter, blocker).orNotFound)
                .serve
                .compile
                .drain
            _ <- logger.info("""
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
""")
          } yield server
      }
    server.as(ExitCode.Success)
  }
}
