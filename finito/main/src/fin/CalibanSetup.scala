package fin

import java.time.LocalDate

import scala.annotation.nowarn
import scala.concurrent.duration._
import scala.util.Try

import caliban.CalibanError.ExecutionError
import caliban.Macros.gqldoc
import caliban._
import caliban.interop.cats.implicits._
import caliban.schema._
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers
import cats.effect.std.Dispatcher
import cats.effect.{Async, Temporal}
import cats.implicits._
import org.typelevel.log4cats.Logger
import natchez.EntryPoint

import fin.Operations._
import fin.Types._

import CalibanError._
import ResponseValue._
import Value._
import FinitoSchema._
//import caliban.wrappers.Wrapper._

object CalibanSetup {

  type Env = zio.Clock with zio.Console

  val freshnessQuery =
    gqldoc("""
{
  collection(name: "My Books", booksPagination: {first: 5, after: 0}) {
    name
    books {
      title
    }
  }
}""")

  def interpreter[F[_]: Async](services: Services[F], ep: EntryPoint[F])(
      implicit
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
      ep
        .root("root_span")
        .use(root =>
          root.put("gql" -> "collections") *> collectionService
            .collections(root)
        ),
      collectionArgs =>
        ep
          .root("root_span")
          .use(root =>
            root.put("gql" -> "collections") *> collectionService
              .collection(collectionArgs, root)
          ),
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

    // val parsingSpan =
    //   new ParsingWrapper[Any] {
    //     def wrap[R1 <: Any](
    //         process: String => zio.ZIO[R1, ParsingError, Document]
    //     ) =
    //       string =>
    //         ep.root("graphql parsing").use { root =>
    //           root.put("gql" -> "parsing") *> process(string)
    //         }
    //   }

    val api = GraphQL.graphQL(RootResolver(queries, mutations))
    (api @@ apolloTracing @@ Wrappers.printErrors)
      .interpreterAsync[F]
      .map(_.provideLayer(zio.ZLayer.succeed(runtime.environment)))
      .map(withErrors(_))
  }

  def keepFresh[F[+_]: Async: Logger](
      interpreter: GraphQLInterpreter[Any, CalibanError],
      timer: Temporal[F]
  )(implicit runtime: zio.Runtime[Env]): F[Nothing] = {
    (interpreter
      .executeAsync[F](freshnessQuery)
      .attempt
      .flatMap { resp =>
        Logger[F].debug(s"Freshness query response: $resp")
      }
      >> timer
        .sleep(1.minutes)).foreverM
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
