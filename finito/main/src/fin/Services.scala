package fin

import cats.Parallel
import cats.arrow.FunctionK
import cats.effect._
import cats.effect.kernel.Clock
import cats.implicits._
import doobie._
import doobie.implicits._
import fs2.compression.Compression
import org.http4s.client.middleware.GZip
import org.typelevel.log4cats.Logger

import fin.persistence.{SqliteBookRepository, SqliteCollectionRepository}
import fin.service.book._
import fin.service.collection._
import fin.service.port._
import fin.service.search._
import fin.service.summary._

final case class Services[F[_]](
    bookInfoService: BookInfoService[F],
    seriesInfoService: SeriesInfoService[F],
    bookManagementService: BookManagementService[F],
    collectionService: CollectionService[F],
    collectionExportService: CollectionExportService[F],
    summaryService: SummaryService[F]
)

object Services {
  def apply[F[_]: Async: Parallel: Logger: Compression](
      serviceResources: ServiceResources[F]
  ): F[Services[F]] = {
    val ServiceResources(client, config, transactor, _, _) = serviceResources
    val clock                                              = Clock[F]
    val collectionRepo  = SqliteCollectionRepository
    val bookRepo        = SqliteBookRepository
    val bookInfoService = GoogleBookInfoService[F](GZip()(client))
    val connectionIOToF = λ[FunctionK[ConnectionIO, F]](_.transact(transactor))
    val wrappedInfoService = BookInfoAugmentationService[F, ConnectionIO](
      bookInfoService,
      bookRepo,
      connectionIOToF
    )
    val collectionService = CollectionServiceImpl[F, ConnectionIO](
      collectionRepo,
      clock,
      connectionIOToF
    )
    val bookManagmentService = BookManagementServiceImpl[F, ConnectionIO](
      bookRepo,
      clock,
      connectionIOToF
    )
    val seriesInfoService =
      WikidataSeriesInfoService[F](client, wrappedInfoService)
    val exportService =
      GoodreadsExportService[F](config.defaultCollection, collectionService)
    val summaryService = SummaryServiceImpl[F, ConnectionIO](
      bookRepo,
      BufferedImageMontageService[F],
      clock,
      connectionIOToF
    )
    SpecialCollectionSetup
      .setup[F, ConnectionIO](
        collectionRepo,
        collectionService,
        bookManagmentService,
        config.defaultCollection,
        config.specialCollections,
        connectionIOToF
      )
      .map { case (wrappedBookManagementService, wrappedCollectionService) =>
        Services[F](
          bookInfoService,
          seriesInfoService,
          wrappedBookManagementService,
          wrappedCollectionService,
          exportService,
          summaryService
        )
      }
  }
}
