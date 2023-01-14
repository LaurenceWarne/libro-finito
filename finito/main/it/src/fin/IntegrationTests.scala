package fin

import java.time.LocalDate

import caliban.client.ArgEncoder._
import caliban.client.Operations._
import caliban.client.SelectionBuilder
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

  val backend: Resource[IO, ClientBackend] =
    Http4sBackend.usingDefaultBlazeClientBuilder[IO]()

  val container: Resource[IO, GenericContainer] =
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
      val createRequest = createCollection(collectionName)(
        Collection.view(UserBook.view, Sort.view, PageInfo.view)
      )
      for {
        // CREATE COLLECTION
        createResponse <- send(uri, backend)(createRequest)
        // UPDATE
        updateRequest = updateCollection(collectionName, newName.some)(
          Collection.view(UserBook.view, Sort.view, PageInfo.view)
        )
        updateResponse <- send(uri, backend)(updateRequest)
        // RETRIEVE COLLECTION
        retrieveRequest = collection(newName)(
          Collection.view(UserBook.view, Sort.view, PageInfo.view)
        )
        retrieveResponse <- send(uri, backend)(retrieveRequest)
      } yield expect(createResponse.name === collectionName) and expect(
        updateResponse.name === newName
      ) and expect(
        retrieveResponse.name === newName
      )
  }

  testUsingUri("createCollection, addBook, collection") {
    case (uri, backend) =>
      val collectionName = "my collection with books"
      val book           = bookTemplate.copy(isbn = "create/add/retrieve")

      val createRequest =
        createCollection(collectionName)(
          Collection.view(UserBook.view, Sort.view, PageInfo.view)
        )
      for {
        // CREATE COLLECTION
        _ <- send(uri, backend)(createRequest)
        // ADD BOOK
        addBookRequest = addBook(collectionName.some, book)(
          Collection.view(UserBook.view, Sort.view, PageInfo.view)
        )
        addBookResponse <- send(uri, backend)(addBookRequest)
        // RETRIEVE COLLECTION
        retrieveRequest = collection(collectionName)(
          Collection.view(UserBook.view, Sort.view, PageInfo.view)
        )
        retrieveResponse <- send(uri, backend)(retrieveRequest)
      } yield expectBooksEqualIgnoringDates(
        viewToUserBook(addBookResponse.books.head),
        inputToUserBook(book)
      ) and expectBooksEqualIgnoringDates(
        viewToUserBook(retrieveResponse.books.head),
        inputToUserBook(book)
      )
  }

  testUsingUri("createCollection, addBook, collection, removeBook") {
    case (uri, backend) =>
      val collectionName = "my collection with books to remove"
      val book           = bookTemplate.copy(isbn = "create/add/retrieve/remove")

      val createRequest =
        createCollection(collectionName)(
          Collection.view(UserBook.view, Sort.view, PageInfo.view)
        )
      for {
        // CREATE COLLECTION
        _ <- send(uri, backend)(createRequest)
        // ADD BOOK
        addBookRequest = addBook(collectionName.some, book)(
          Collection.view(UserBook.view, Sort.view, PageInfo.view)
        )
        addBookResponse <- send(uri, backend)(addBookRequest)
        // REMOVE BOOK
        removeBookRequest = removeBook(collectionName, book.isbn)
        _ <- send(uri, backend)(removeBookRequest)
        // RETRIEVE COLLECTION
        retrieveRequest = collection(collectionName)(
          Collection.view(UserBook.view, Sort.view, PageInfo.view)
        )
        retrieveResponse <- send(uri, backend)(retrieveRequest)
      } yield expectBooksEqualIgnoringDates(
        viewToUserBook(addBookResponse.books.head),
        inputToUserBook(book)
      ) and expect(retrieveResponse.books.isEmpty)
  }

  testUsingUri("createCollection, addBook, startReading") {
    case (uri, backend) =>
      val collectionName = "my collection with book to start"
      val book           = bookTemplate.copy(isbn = "create/add/start")
      val startedDate    = "2012-03-12"

      val createRequest =
        createCollection(collectionName)(
          Collection.view(UserBook.view, Sort.view, PageInfo.view)
        )
      for {
        // CREATE COLLECTION
        _ <- send(uri, backend)(createRequest)
        // ADD BOOK
        addBookRequest = addBook(collectionName.some, book)(
          Collection.view(UserBook.view, Sort.view, PageInfo.view)
        )
        _ <- send(uri, backend)(addBookRequest)
        // START BOOK
        startRequest = startReading(book, startedDate.some)(UserBook.view)
        startResponse <- send(uri, backend)(startRequest)
      } yield expect(startResponse.startedReading.exists(_ == startedDate))
  }

  testUsingUri("createCollection, addBook, finishReading") {
    case (uri, backend) =>
      val collectionName = "my collection with book to finish"
      val book           = bookTemplate.copy(isbn = "create/add/finish")
      val finishedDate   = "2012-03-12"

      val createRequest =
        createCollection(collectionName)(
          Collection.view(UserBook.view, Sort.view, PageInfo.view)
        )
      for {
        // CREATE COLLECTION
        _ <- send(uri, backend)(createRequest)
        // ADD BOOK
        addBookRequest = addBook(collectionName.some, book)(
          Collection.view(UserBook.view, Sort.view, PageInfo.view)
        )
        _ <- send(uri, backend)(addBookRequest)
        // FINISH BOOK
        finishRequest = finishReading(book, finishedDate.some)(UserBook.view)
        finishResponse <- send(uri, backend)(finishRequest)
      } yield expect(finishResponse.lastRead.exists(_ == finishedDate))
  }

  testUsingUri("books") {
    case (uri, backend) =>
      val title      = "Lord of the Rings"
      val authors    = "Tolkien"
      val maxResults = 10
      val lang       = "en"
      val booksRequest =
        books(title.some, authors.some, maxResults.some, lang.some)(
          UserBook.view
        )
      for {
        // KEYWORD SEARCH
        booksResponse <- send(uri, backend)(booksRequest)
      } yield expect(booksResponse.nonEmpty)
  }

  testUsingUri("series") {
    case (uri, backend) =>
      val bookInput = bookTemplate.copy(
        title = "Neuromancer",
        authors = List("William Gibson")
      )
      val seriesRequest = series(bookInput)(UserBook.view)
      for {
        // SEARCH SERIES
        seriesResponse <- send(uri, backend)(seriesRequest)
      } yield expect(seriesResponse.nonEmpty)
  }

  testUsingUri("summary") {
    case (uri, backend) =>
      val book = bookTemplate.copy(
        isbn = "summary",
        thumbnailUri =
          "https://user-images.githubusercontent.com/17688577/144673930-add9233d-9308-4972-8043-2f519d808874.png"
      )
      val addBookRequest = addBook(None, book)(
        Collection.view(UserBook.view, Sort.view, PageInfo.view)
      )
      for {
        // ADD BOOK
        _ <- send(uri, backend)(addBookRequest)
        // SUMMARY
        summaryRequest = summary(None, None, None, true)(Summary.view)
        summaryResponse <- send(uri, backend)(summaryRequest)
      } yield expect(summaryResponse.added > 0)
  }

  private def send[R: IsOperation, A](uri: Uri, backend: ClientBackend)(
      selection: SelectionBuilder[R, A]
  ): IO[A] =
    selection
      .toRequest(uri)
      .send(backend)
      .map(_.body.leftMap(err => new Exception(err.toString)))
      .flatMap(IO.fromEither)

  def expectBooksEqualIgnoringDates(
      b1: Types.UserBook,
      b2: Types.UserBook
  ): Expectations =
    expect(b1.title === b2.title) and expect(
      b1.authors === b2.authors
    ) and expect(b1.description === b2.description) and expect(
      b1.isbn === b2.isbn
    ) and expect(b1.rating === b2.rating)

  def viewToUserBook(
      book: UserBook.UserBookView
  ): Types.UserBook =
    Types.UserBook(
      title = book.title,
      authors = book.authors,
      description = book.description,
      isbn = book.isbn,
      thumbnailUri = book.thumbnailUri,
      dateAdded = book.dateAdded.map(LocalDate.parse),
      rating = book.rating,
      startedReading = book.startedReading.map(LocalDate.parse),
      lastRead = book.lastRead.map(LocalDate.parse)
    )

  def inputToUserBook(
      book: BookInput,
      dateAdded: Option[LocalDate] = None,
      rating: Option[Int] = None,
      startedReading: Option[LocalDate] = None,
      lastRead: Option[LocalDate] = None
  ): Types.UserBook =
    Types.UserBook(
      book.title,
      book.authors,
      book.description,
      book.isbn,
      book.thumbnailUri,
      dateAdded,
      rating,
      startedReading,
      lastRead
    )
}
