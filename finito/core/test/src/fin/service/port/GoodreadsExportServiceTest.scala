package fin.service.port

import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import weaver._

import fin.Types._
import fin.fixtures
import fin.service.collection._
import fin.service.port._

object GoodreadsExportServiceTest extends IOSuite {

  val defaultCollectionBook = fixtures.bookInput
  val defaultCollection     = "default collection"

  override type Res = GoodreadsExportService[IO]
  override def sharedResource: Resource[IO, GoodreadsExportService[IO]] =
    Resource.eval(Ref.of[IO, List[Collection]](List.empty).flatMap { ref =>
      val collectionService = CollectionServiceImpl[IO, IO](
        new InMemoryCollectionRepository(ref),
        Clock[IO],
        FunctionK.id[IO]
      )
      collectionService
        .createCollection(
          MutationsCreateCollectionArgs(
            defaultCollection,
            None,
            None,
            None
          )
        ) *> collectionService
        .addBookToCollection(
          MutationsAddBookArgs(defaultCollection.some, defaultCollectionBook)
        )
        .as(GoodreadsExportService(defaultCollection.some, collectionService))
    })

  private def exportArgs(collection: Option[String] = defaultCollection.some) =
    QueriesExportArgs(PortType.Goodreads, collection)

  test("exportCollection csv contains collection data") { exportService =>
    val args = exportArgs()
    for {
      csv <- exportService.exportCollection(args)
    } yield expect(csv.contains(defaultCollectionBook.title)) &&
      expect(csv.contains(defaultCollectionBook.isbn)) &&
      expect(
        csv.contains(defaultCollectionBook.authors.headOption.getOrElse(""))
      )
  }

  test("exportCollection defaults to exporting default collection") {
    exportService =>
      val args = exportArgs(None)
      for {
        csv <- exportService.exportCollection(args)
      } yield expect(csv.contains(defaultCollectionBook.title)) &&
        expect(csv.contains(defaultCollectionBook.isbn)) &&
        expect(
          csv.contains(defaultCollectionBook.authors.headOption.getOrElse(""))
        )
  }

  test("exportCollection errors when no collection specified") { _ =>
    val args = exportArgs()
    for {
      ref <- Ref.of[IO, List[Collection]](List.empty)
      collectionService = CollectionServiceImpl[IO, IO](
        new InMemoryCollectionRepository(ref),
        Clock[IO],
        FunctionK.id[IO]
      )
      exportService = GoodreadsExportService(None, collectionService)
      response <- exportService.exportCollection(args).attempt
    } yield expect(response.isLeft)
  }
}
