package fin

import caliban.CalibanError.ExecutionError
import caliban._
import caliban.interop.cats.implicits._
import cats.effect.Effect
import cats.implicits._

import fin.Operations._
import fin.service.{BookInfoService, BookManagementService, CollectionService}

object CalibanSetup {

  def interpreter[F[_]: Effect](
      bookInfoService: BookInfoService[F],
      bookManagementService: BookManagementService[F],
      collectionService: CollectionService[F]
  )(implicit
      runtime: zio.Runtime[Any]
  ): F[GraphQLInterpreter[Any, CalibanError]] = {
    val queries = Queries[F](
      booksArgs => bookInfoService.search(booksArgs),
      bookArgs => bookInfoService.fromIsbn(bookArgs),
      collectionService.collections,
      collectionArgs => collectionService.collection(collectionArgs)
    )
    val mutations = Mutations[F](
      args => collectionService.createCollection(args),
      args => collectionService.deleteCollection(args).map(_ => None),
      args => collectionService.updateCollection(args),
      args => collectionService.addBookToCollection(args),
      args => collectionService.removeBookFromCollection(args).map(_ => None),
      args => bookManagementService.startReading(args),
      args => bookManagementService.finishReading(args),
      args => bookManagementService.rateBook(args),
      args => bookManagementService.createBook(args)
    )
    val api = GraphQL.graphQL(RootResolver(queries, mutations))
    api.interpreterAsync[F].map(withErrors(_))
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
