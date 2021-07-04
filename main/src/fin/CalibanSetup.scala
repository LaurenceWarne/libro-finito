package fin

import caliban.CalibanError.ExecutionError
import caliban._
import caliban.interop.cats.implicits._
import cats.effect.IO

import fin.Operations._
import fin.service.BookInfoService
import fin.service.CollectionService

object CalibanSetup {

  def interpreter(
      bookInfoService: BookInfoService[IO],
      collectionService: CollectionService[IO]
  )(implicit
      runtime: zio.Runtime[Any]
  ): IO[GraphQLInterpreter[Any, CalibanError]] = {
    val queries = Queries(
      booksArgs => bookInfoService.search(booksArgs),
      bookArgs => bookInfoService.fromIsbn(bookArgs),
      collectionService.collections,
      collectionArgs => collectionService.collection(collectionArgs)
    )
    val mutations = Mutations(
      args => collectionService.createCollection(args),
      args => collectionService.deleteCollection(args).map(_ => None),
      args => collectionService.changeCollectionName(args),
      args => collectionService.addBookToCollection(args),
      args => collectionService.removeBookFromCollection(args).map(_ => None)
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
