package fin

import caliban.interop.cats.implicits._
import caliban.CalibanError.ExecutionError
import caliban._
import cats.effect.IO
import cats.implicits._

import fin.Operations._
import fin.Types._
import fin.service.BookInfoService

object CalibanSetup {

  def interpreter(
      bookInfoService: BookInfoService[IO]
  )(implicit
      runtime: zio.Runtime[Any]
  ): IO[GraphQLInterpreter[Any, CalibanError]] = {
    val queries = Queries(
      booksArgs => bookInfoService.search(booksArgs),
      bookArgs => bookInfoService.fromIsbn(bookArgs),
      List.empty[Collection].pure[IO],
      _ => ???
    )
    val mutations = Mutations(
      _ => ???,
      _ => ???,
      _ => ???,
      _ => ???
    )
    val api = GraphQL.graphQL(RootResolver(queries, mutations))
    api.interpreterAsync[IO].map(withErrors(_))
  }

  private def withErrors[R](
      interpreter: GraphQLInterpreter[R, CalibanError]
  ): GraphQLInterpreter[R, CalibanError] =
    interpreter.mapError {
      case err @ ExecutionError(_, _, _, Some(wrappedError), _) =>
        err.copy(msg = wrappedError.getMessage)
      case err => err
    }
}
