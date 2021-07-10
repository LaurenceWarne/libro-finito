package fin.service

import cats.effect._
import cats.effect.concurrent.Ref
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.Types._
import fin.persistence.CollectionRepository

object DefaultCollectionServiceImplTest extends IOSuite {

  implicit def unsafeLogger: Logger[IO] = Slf4jLogger.getLogger

  val collectionName = "My Books"

  override type Res = (CollectionRepository[IO], DefaultCollectionService[IO])
  override def sharedResource: Resource[IO, Res] =
    Resource.eval(Ref.of[IO, List[Collection]](List.empty).map { ref =>
      val inMemoryRepo = new InMemoryCollectionRepository[IO](ref)
      (
        inMemoryRepo,
        DefaultCollectionServiceImpl(
          collectionName,
          inMemoryRepo
        )
      )
    })

  test("createDefaultCollection creates collection") {
    case (repo, service) =>
      for {
        _               <- service.createDefaultCollection
        maybeCollection <- repo.collection(collectionName)
      } yield expect(maybeCollection.nonEmpty)
  }

  test("createDefaultCollection does not error if collection already exists") {
    case (repo, service) =>
      for {
        _        <- repo.createCollection(collectionName)
        response <- service.createDefaultCollection.attempt
      } yield expect(response.isRight)
  }
}
