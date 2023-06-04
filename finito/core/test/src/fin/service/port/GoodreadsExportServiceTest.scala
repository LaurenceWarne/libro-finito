package fin.service.port

import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import weaver._

import fin.Types._
import fin.service.collection._
import fin.service.port._

object GoodreadsExportServiceTest extends IOSuite {

  val book = BookInput(
    "Neuromancer",
    List("William Gibson"),
    "description",
    "isbn",
    "???"
  )
  val defaultCollection = "default collection"

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
        .addBookToCollection(MutationsAddBookArgs(defaultCollection.some, book))
        .as(GoodreadsExportService(defaultCollection.some, collectionService))
    })

  private def exportArgs(collection: Option[String] = defaultCollection.some) =
    QueriesExportArgs(PortType.Goodreads, collection)

  test("exportCollection csv contains collection data") { exportService =>
    val args = exportArgs()
    for {
      csv <- exportService.exportCollection(args)
    } yield expect(csv.contains(book.title)) &&
      expect(csv.contains(book.isbn)) &&
      expect(csv.contains(book.authors.headOption.getOrElse("")))
  }
}
