package fin

import cats.Parallel
import cats.arrow.FunctionK
import cats.effect._
import cats.effect.kernel.Clock
import cats.implicits._
import doobie._
import doobie.implicits._
import org.http4s.client.middleware.GZip
import org.typelevel.log4cats.Logger

import fin.persistence.{SqliteBookRepository, SqliteCollectionRepository}
import fin.service.book._
import fin.service.collection._
import fin.service.search.{GoogleBookInfoService, _}
import fin.service.summary._

final case class Services[F[_]](
    bookInfoService: BookInfoService[F],
    seriesInfoService: SeriesInfoService[F],
    bookManagementService: BookManagementService[F],
    collectionService: CollectionService[F],
    summaryService: SummaryService[F]
)

object Services {
  def apply[F[_]: Async: Parallel: Logger](
      serviceResources: ServiceResources[F]
  ): F[Services[F]] = {
    val ServiceResources(client, config, transactor, _, ep) = serviceResources
    val clock                                               = Clock[F]
    val collectionRepo                                      = SqliteCollectionRepository
    val bookRepo                                            = SqliteBookRepository
    val bookInfoService                                     = GoogleBookInfoService[F](GZip()(client))
    for {
      connectionIOToF <-
        ep
          .root("db")
          .use(root =>
            (λ[FunctionK[ConnectionIO, F]])(
              _.transact(transactor).run(root)
            ).pure[F]
          )
      wrappedInfoService = BookInfoAugmentationService[F, ConnectionIO](
        bookInfoService,
        bookRepo,
        connectionIOToF
      )
      collectionService = CollectionServiceImpl[F, ConnectionIO](
        collectionRepo,
        clock,
        connectionIOToF
      )
      bookManagmentService = BookManagementServiceImpl[F, ConnectionIO](
        bookRepo,
        clock,
        connectionIOToF
      )
      seriesInfoService =
        WikidataSeriesInfoService[F](client, wrappedInfoService)
      summaryService = SummaryServiceImpl[F, ConnectionIO](
        bookRepo,
        BufferedImageMontageService[F],
        clock,
        connectionIOToF
      )
      (wrappedBookManagementService, wrappedCollectionService) <-
        SpecialCollectionSetup
          .setup[F, ConnectionIO](
            collectionRepo,
            collectionService,
            bookManagmentService,
            config.defaultCollection,
            config.specialCollections,
            connectionIOToF
          )
    } yield Services[F](
      bookInfoService,
      seriesInfoService,
      wrappedBookManagementService,
      wrappedCollectionService,
      summaryService
    )
  }
}
