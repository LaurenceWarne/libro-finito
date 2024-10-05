package fin

import java.time.LocalDate

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

  type Env = zio.Clock with zio.Console

  def interpreter[F[_]: Async](services: Services[F])(implicit
      runtime: zio.Runtime[Env],
      dispatcher: Dispatcher[F]
  ): F[GraphQLInterpreter[Any, CalibanError]] = {
    val Services(
      bookInfoService,
      seriesInfoService,
      bookManagementService,
      collectionService,
      exportService,
      summaryService
    ) = services
    val queries = Query[F](
      booksArgs => bookInfoService.search(booksArgs),
      bookArgs => bookInfoService.fromIsbn(bookArgs),
      seriesArgs => seriesInfoService.series(seriesArgs),
      collectionService.collections,
      collectionArgs => collectionService.collection(collectionArgs),
      exportArgs => exportService.exportCollection(exportArgs),
      summaryArgs => summaryService.summary(summaryArgs)
    )
    val mutations = Mutation[F](
      args => collectionService.createCollection(args),
      args => collectionService.deleteCollection(args).as(None),
      args => collectionService.updateCollection(args),
      args => collectionService.addBookToCollection(args),
      args => collectionService.removeBookFromCollection(args).as(None),
      args => bookManagementService.startReading(args),
      args => bookManagementService.finishReading(args),
      args => bookManagementService.rateBook(args),
      args => bookManagementService.addBookReview(args),
      args => bookManagementService.createBook(args),
      args => bookManagementService.deleteBookData(args).as(None),
      _ => ???
    )
    val api = graphQL(RootResolver(queries, mutations))
    (api @@ apolloTracing() @@ Wrappers.printErrors)
      .interpreterF[F]
      .map(_.provideLayer(zio.ZLayer.succeed(runtime.environment)))
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

  implicit val localDateSchema: Schema[Any, LocalDate] =
    Schema.scalarSchema(
      "DateTime",
      None,
      None,
      None,
      d => StringValue(d.toString)
    )

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

  implicit val sortTypeSchema: Schema[Any, SortType] = Schema.gen[Any, SortType]

  implicit val portTypeSchema: Schema[Any, PortType] = Schema.gen[Any, PortType]

  implicit val pageInfoSchema: Schema[Any, PageInfo] = Schema.gen[Any, PageInfo]

  implicit val sortSchema: Schema[Any, Sort] = Schema.gen[Any, Sort]

  implicit val userBookSchema: Schema[Any, UserBook] = Schema.gen[Any, UserBook]

  implicit val collectionSchema: Schema[Any, Collection] =
    Schema.gen[Any, Collection]

  implicit val summarySchema: Schema[Any, Summary] = Schema.gen[Any, Summary]

  implicit val bookInputSchema: Schema[Any, BookInput] =
    Schema.gen[Any, BookInput].rename("BookInput", "BookInput".some)

  implicit val montageInputSchema: Schema[Any, MontageInput] =
    Schema.gen[Any, MontageInput].rename("MontageInput", "MontageInput".some)

  implicit val paginationInputSchema: Schema[Any, PaginationInput] =
    Schema
      .gen[Any, PaginationInput]
      .rename("PaginationInput", "PaginationInput".some)

  implicit val queriesBooksArgsSchema: Schema[Any, QueryBooksArgs] =
    Schema.gen[Any, QueryBooksArgs]

  implicit val queriesBookArgsSchema: Schema[Any, QueryBookArgs] =
    Schema.gen[Any, QueryBookArgs]

  implicit val queriesSeriesArgsSchema: Schema[Any, QuerySeriesArgs] =
    Schema.gen[Any, QuerySeriesArgs]

  implicit val queriesCollectionArgsSchema: Schema[Any, QueryCollectionArgs] =
    Schema.gen[Any, QueryCollectionArgs]

  implicit val queriesExportArgsSchema: Schema[Any, QueryExportArgs] =
    Schema.gen[Any, QueryExportArgs]

  implicit val queriesSummaryArgsSchema: Schema[Any, QuerySummaryArgs] =
    Schema.gen[Any, QuerySummaryArgs]

  implicit val mutationsCreateCollectionArgsSchema
      : Schema[Any, MutationCreateCollectionArgs] =
    Schema.gen[Any, MutationCreateCollectionArgs]

  implicit val mutationsDeleteCollectionArgsSchema
      : Schema[Any, MutationDeleteCollectionArgs] =
    Schema.gen[Any, MutationDeleteCollectionArgs]

  implicit val mutationsUpdateCollectionArgsSchema
      : Schema[Any, MutationUpdateCollectionArgs] =
    Schema.gen[Any, MutationUpdateCollectionArgs]

  implicit val mutationsAddBookArgsSchema: Schema[Any, MutationAddBookArgs] =
    Schema.gen[Any, MutationAddBookArgs]

  implicit val mutationsRemoveBookArgsSchema
      : Schema[Any, MutationRemoveBookArgs] =
    Schema.gen[Any, MutationRemoveBookArgs]

  implicit val mutationsStartReadingArgsSchema
      : Schema[Any, MutationStartReadingArgs] =
    Schema.gen[Any, MutationStartReadingArgs]

  implicit val mutationsFinishReadingArgsSchema
      : Schema[Any, MutationFinishReadingArgs] =
    Schema.gen[Any, MutationFinishReadingArgs]

  implicit val mutationsRateBookArgsSchema: Schema[Any, MutationRateBookArgs] =
    Schema.gen[Any, MutationRateBookArgs]

  implicit val mutationsAddBookReviewArgsSchema
      : Schema[Any, MutationAddBookReviewArgs] =
    Schema.gen[Any, MutationAddBookReviewArgs]

  implicit val mutationsCreateBookArgsSchema
      : Schema[Any, MutationCreateBookArgs] =
    Schema.gen[Any, MutationCreateBookArgs]

  implicit val mutationsDeleteBookDataArgsSchema
      : Schema[Any, MutationDeleteBookDataArgs] =
    Schema.gen[Any, MutationDeleteBookDataArgs]

  implicit val mutationsImportArgsSchema: Schema[Any, MutationImportArgs] =
    Schema.gen[Any, MutationImportArgs]

  implicit val sortTypeArg: ArgBuilder[SortType]               = ArgBuilder.gen
  implicit val portTypeArg: ArgBuilder[PortType]               = ArgBuilder.gen
  implicit val pageInfoArg: ArgBuilder[PageInfo]               = ArgBuilder.gen
  implicit val sortArg: ArgBuilder[Sort]                       = ArgBuilder.gen
  implicit val userBookArg: ArgBuilder[UserBook]               = ArgBuilder.gen
  implicit val collectionArg: ArgBuilder[Collection]           = ArgBuilder.gen
  implicit val summaryArg: ArgBuilder[Summary]                 = ArgBuilder.gen
  implicit val bookInputArg: ArgBuilder[BookInput]             = ArgBuilder.gen
  implicit val montageInputArg: ArgBuilder[MontageInput]       = ArgBuilder.gen
  implicit val paginationInputArg: ArgBuilder[PaginationInput] = ArgBuilder.gen
  implicit val queryBooksArgsArg: ArgBuilder[QueryBooksArgs]   = ArgBuilder.gen
  implicit val queryBookArgsArg: ArgBuilder[QueryBookArgs]     = ArgBuilder.gen
  implicit val querySeriesArgsArg: ArgBuilder[QuerySeriesArgs] = ArgBuilder.gen
  implicit val queryCollectionArgsArg: ArgBuilder[QueryCollectionArgs] =
    ArgBuilder.gen
  implicit val queryExportArgsArg: ArgBuilder[QueryExportArgs] = ArgBuilder.gen
  implicit val querySummaryArgsArg: ArgBuilder[QuerySummaryArgs] =
    ArgBuilder.gen
  implicit val mutationCreateCollectionArgsArg
      : ArgBuilder[MutationCreateCollectionArgs] = ArgBuilder.gen
  implicit val mutationDeleteCollectionArgsArg
      : ArgBuilder[MutationDeleteCollectionArgs] = ArgBuilder.gen
  implicit val mutationUpdateCollectionArgsArg
      : ArgBuilder[MutationUpdateCollectionArgs] = ArgBuilder.gen
  implicit val mutationAddBookArgsArg: ArgBuilder[MutationAddBookArgs] =
    ArgBuilder.gen
  implicit val mutationRemoveBookArgsArg: ArgBuilder[MutationRemoveBookArgs] =
    ArgBuilder.gen
  implicit val mutationStartReadingArgsArg
      : ArgBuilder[MutationStartReadingArgs] = ArgBuilder.gen
  implicit val mutationFinishReadingArgsArg
      : ArgBuilder[MutationFinishReadingArgs] = ArgBuilder.gen
  implicit val mutationRateBookArgsArg: ArgBuilder[MutationRateBookArgs] =
    ArgBuilder.gen
  implicit val mutationAddBookReviewArgsArg
      : ArgBuilder[MutationAddBookReviewArgs] = ArgBuilder.gen
  implicit val mutationCreateBookArgsArg: ArgBuilder[MutationCreateBookArgs] =
    ArgBuilder.gen
  implicit val mutationDeleteBookDataArgsArg
      : ArgBuilder[MutationDeleteBookDataArgs] = ArgBuilder.gen
  implicit val mutationImportArgsArg: ArgBuilder[MutationImportArgs] =
    ArgBuilder.gen

  implicit def querySchema[F[_]](implicit
      dispatcher: Dispatcher[F]
  ): Schema[Any, Query[F]] =
    Schema.gen[Any, Query[F]]

  implicit def mutationSchema[F[_]](implicit
      dispatcher: Dispatcher[F]
  ): Schema[Any, Mutation[F]] =
    Schema.gen[Any, Mutation[F]]
}
