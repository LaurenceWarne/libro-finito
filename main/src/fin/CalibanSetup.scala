package fin

import caliban.interop.cats.implicits._
import caliban.CalibanError.ExecutionError
import caliban._
import cats.effect.IO
import cats.implicits._

import fin.Operations._
import fin.persistence.CollectionRepository
import fin.Types._
import fin.service.BookInfoService

object CalibanSetup {

  def interpreter(
      bookInfoService: BookInfoService[IO],
      collectionRepo: CollectionRepository[IO]
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
      args => collectionRepo.createCollection(args.name),
      _ => ???,
      _ => ???,
      _ => ???
    )
    val api = GraphQL.graphQL(RootResolver(queries, mutations))
    api.interpreterAsync[IO].map(withErrors(_))
  }

  // 'Effect failure' is from this line:
  // https://github.com/ghostdogpr/caliban/blob/2e4d6ec571ca15a1b66f6e4f8a0ef0c94c80513d/core/src/main/scala/caliban/execution/Executor.scala#L224
  private def withErrors[R](
      interpreter: GraphQLInterpreter[R, CalibanError]
  ): GraphQLInterpreter[R, CalibanError] =
    interpreter.mapError {
      case err @ ExecutionError(_, _, _, Some(wrappedError), _) =>
        err.copy(msg = wrappedError.getMessage)
      case err => err
    }
}
