package fin

import java.time.LocalDate

import scala.annotation.nowarn
import scala.util.Try

import caliban.CalibanError.ExecutionError
import caliban._
import caliban.interop.cats.implicits._
import caliban.schema._
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers
import cats.effect.Async
import cats.effect.std.Dispatcher
import cats.implicits._

import fin.Operations._
import fin.Types._

import CalibanError._
import ResponseValue._
import Value._
import FinitoSchema._

object CalibanSetup {

  type Env = zio.clock.Clock with zio.console.Console

  def interpreter[F[_]: Async](services: Services[F])(implicit
      runtime: zio.Runtime[Env],
      @nowarn dispatcher: Dispatcher[F]
  ): F[GraphQLInterpreter[Any, CalibanError]] = {
    val Services(
      bookInfoService,
      seriesInfoService,
      bookManagementService,
      collectionService,
      summaryService
    ) = services
    val queries = Queries[F](
      booksArgs => bookInfoService.search(booksArgs),
      bookArgs => bookInfoService.fromIsbn(bookArgs),
      seriesArgs => seriesInfoService.series(seriesArgs),
      collectionService.collections,
      collectionArgs => collectionService.collection(collectionArgs),
      _ => ???,
      summaryArgs => summaryService.summary(summaryArgs)
    )
    val mutations = Mutations[F](
      args => collectionService.createCollection(args),
      args => collectionService.deleteCollection(args).as(None),
      args => collectionService.updateCollection(args),
      args => collectionService.addBookToCollection(args),
      args => collectionService.removeBookFromCollection(args).as(None),
      args => bookManagementService.startReading(args),
      args => bookManagementService.finishReading(args),
      args => bookManagementService.rateBook(args),
      args => bookManagementService.createBook(args),
      args => bookManagementService.deleteBookData(args).as(None),
      _ => ???
    )
    val api = GraphQL.graphQL(RootResolver(queries, mutations))
    (api @@ apolloTracing @@ Wrappers.printErrors)
      .interpreterAsync[F]
      .map(_.provide(runtime.environment))
      .map(withErrors(_))
  }

  // 'Effect failure' is from this line:
  // https://github.com/ghostdogpr/caliban/blob/2e4d6ec571ca15a1b66f6e4f8a0ef0c94c80513d/core/src/main/scala/caliban/execution/Executor.scala#L224
  private def withErrors[R](
      interpreter: GraphQLInterpreter[R, CalibanError]
  ): GraphQLInterpreter[R, CalibanError] =
    interpreter.mapError {
      case err @ ExecutionError(_, _, _, Some(wrappedError: FinitoError), _) =>
        err.copy(
          msg = wrappedError.getMessage,
          extensions = ObjectValue(
            List(("errorCode", StringValue(wrappedError.errorCode)))
          ).some
        )
      case err: ValidationError =>
        err.copy(extensions =
          ObjectValue(List(("errorCode", StringValue("VALIDATION_ERROR")))).some
        )
      case err: ParsingError =>
        err.copy(extensions =
          ObjectValue(List(("errorCode", StringValue("PARSING_ERROR")))).some
        )
      case err: ExecutionError =>
        err.copy(extensions =
          ObjectValue(List(("errorCode", StringValue("UNKNOWN")))).some
        )
    }
}

object FinitoSchema {
  implicit val bookInputSchema: Schema[Any, BookInput] =
    Schema.gen[Any, BookInput].rename("BookInput", "BookInput".some)

  implicit val montageInputSchema: Schema[Any, MontageInput] =
    Schema.gen[Any, MontageInput].rename("MontageInput", "MontageInput".some)

  implicit val localDateSchema: Schema[Any, LocalDate] =
    Schema.scalarSchema("DateTime", None, None, d => StringValue(d.toString))

  implicit val localDateArgBuilder: ArgBuilder[LocalDate] = {
    case StringValue(value) =>
      Try(LocalDate.parse(value))
        .fold(
          ex =>
            Left(
              ExecutionError(
                s"Can't parse $value into a LocalDate",
                innerThrowable = Some(ex)
              )
            ),
          Right(_)
        )
    case other =>
      Left(ExecutionError(s"Can't build a LocalDate from input $other"))
  }
}
