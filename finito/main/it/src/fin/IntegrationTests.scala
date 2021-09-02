package fin

import cats.effect._
import cats.implicits._
import com.dimafeng.testcontainers._
import org.testcontainers.containers.wait.strategy.Wait
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.client3.http4s._
import sttp.model.Uri
import weaver._

import fin.config.ServiceConfig

import Uri._

object IntegrationTests extends IOSuite {

  import Client._
  import Client.Mutations._
  import Client.Queries._

  type ClientBackend = SttpBackend[IO, Fs2Streams[IO]]

  def backend: Resource[IO, ClientBackend] =
    Http4sBackend.usingDefaultBlazeClientBuilder[IO]()

  def container: Resource[IO, GenericContainer] =
    Resource.make(
      for {
        container <- IO(
          GenericContainer(
            "finito-main",
            exposedPorts = Seq(ServiceConfig.defaultPort),
            waitStrategy = Wait
              .forHttp("/version")
              .forPort(ServiceConfig.defaultPort)
          )
        )
        _ <- IO(container.start())
      } yield container
    )(container => IO(container.stop()))

  override type Res = (ClientBackend, GenericContainer)
  override def sharedResource: Resource[IO, (ClientBackend, GenericContainer)] =
    backend.product(container)
  override def maxParallelism = 1

  val bookTemplate = BookInput(
    "my title",
    List("my author"),
    "my desc",
    "isbn",
    "my thumbnail"
  )

  def testUsingUri(
      name: String
  )(block: (Uri, ClientBackend) => IO[Expectations]) =
    test(name) {
      case (backend, container) =>
        val mappedPort = container.mappedPort(ServiceConfig.defaultPort)
        val uri        = uri"http://localhost:$mappedPort/api/graphql"
        block(uri, backend)
    }

  testUsingUri("createCollection, updateCollection, collection") {
    case (uri, backend) =>
      val collectionName = "my collection"
      val newName        = "my new collection"
      val createRequest  = createCollection(collectionName)(Collection.name)
      for {
        // CREATE
        createResponse <- createRequest.toRequest(uri).send(backend).map(_.body)
        createName     <- IO.fromEither(createResponse)
        // UPDATE
        updateRequest =
          updateCollection(collectionName, newName.some)(Collection.name)
        updateResponse <- updateRequest.toRequest(uri).send(backend).map(_.body)
        updateName     <- IO.fromEither(updateResponse)
        // RETRIEVE
        retrieveRequest = collection(newName)(Collection.name)
        retrieveResponse <-
          retrieveRequest.toRequest(uri).send(backend).map(_.body)
        retrieveName <- IO.fromEither(retrieveResponse)
      } yield expect(createName === collectionName) and expect(
        updateName === newName
      ) and expect(
        retrieveName === newName
      )
  }

  testUsingUri("createCollection, addBook, collection") {
    case (uri, backend) =>
      val collectionName = "my collection with books"
      val book           = bookTemplate.copy(isbn = "create/add/retrieve")

      val createRequest = createCollection(collectionName)(Collection.name)
      for {
        // CREATE
        _ <- createRequest.toRequest(uri).send(backend)
        // ADD BOOK
        addBookRequest = addBook(collectionName.some, book)(
          Collection.name ~ Collection.books(UserBook.isbn)
        )
        addBookResponse <-
          addBookRequest.toRequest(uri).send(backend).map(_.body)
        // RETRIEVE
        retrieveRequest = collection(collectionName)(
          Collection.name ~ Collection.books(UserBook.isbn)
        )
        retrieveResponse <-
          retrieveRequest.toRequest(uri).send(backend).map(_.body)
      } yield expect(
        addBookResponse.exists(_ === (collectionName, List(book.isbn)))
      ) and expect(
        retrieveResponse.exists(_ === (collectionName, List(book.isbn)))
      )
  }
}
