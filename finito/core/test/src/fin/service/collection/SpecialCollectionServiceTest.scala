package fin.service.collection

import cats.effect.concurrent.Ref
import cats.effect.{IO, Resource}
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.BookConversions._
import fin.Types._
import fin.implicits._

object SpecialCollectionServiceTest extends IOSuite {

  val otherCollection           = "other collection"
  val hook1Collection           = "default"
  val hook2Collection           = "cool"
  val hook3Collection           = "my ratings"
  val hookNotCreatedCollection  = "uncreated"
  val hookAlwaysFalseCollection = "always false"
  val collectionHooks = List(
    CollectionHook(hook1Collection, HookType.Add, "add = true"),
    CollectionHook(
      hook2Collection,
      HookType.Add,
      "if string.find(title, \"cool\") then add = true else remove = true end"
    ),
    CollectionHook(hook3Collection, HookType.Rate, "add = true"),
    CollectionHook(
      hookNotCreatedCollection,
      HookType.Add,
      "if title == \"special\" then add = true end"
    ),
    CollectionHook(
      hookAlwaysFalseCollection,
      HookType.Add,
      "add = false"
    )
  )
  val baseBook =
    BookInput(
      "title",
      List("auth"),
      "description",
      "isbn",
      "thumbnail uri"
    )
  val book2     = baseBook.copy(title = "my cool book")
  val argsBook2 = MutationsAddBookArgs(otherCollection.some, book2)

  implicit def unsafeLogger: Logger[IO] = Slf4jLogger.getLogger

  override type Res = CollectionService[IO]
  override def sharedResource: Resource[IO, CollectionService[IO]] =
    for {
      colRef <- Resource.eval(Ref.of[IO, List[Collection]](List.empty))
      wrappedCollectionService = CollectionServiceImpl(
        new InMemoryCollectionRepository(colRef)
      )
      hookExecutionService = HookExecutionServiceImpl[IO]
      specialCollectionService = SpecialCollectionService(
        hook1Collection.some,
        wrappedCollectionService,
        collectionHooks,
        hookExecutionService
      )
      _ <- (List(
          otherCollection,
          hook1Collection,
          hook2Collection,
          hook3Collection
        )).traverse { collection =>
        Resource.eval(
          wrappedCollectionService.createCollection(
            MutationsCreateCollectionArgs(collection, None)
          )
        )
      }
    } yield specialCollectionService

  test("addBookToCollection adds for matching hook, but not for others") {
    collectionService =>
      val book     = baseBook.copy(isbn = "isbn1")
      val argsBook = MutationsAddBookArgs(otherCollection.some, book)
      for {
        _ <- collectionService.addBookToCollection(argsBook)
        hook1Response <-
          collectionService.collection(QueriesCollectionArgs(hook1Collection))
        hook2Response <-
          collectionService.collection(QueriesCollectionArgs(hook2Collection))
      } yield expect(hook1Response.books.contains(toUserBook(book))) and expect(
        !hook2Response.books.contains(toUserBook(book))
      )
  }

  test("addBookToCollection removes for matching hook, but not for others") {
    collectionService =>
      val book     = baseBook.copy(isbn = "isbn2")
      val userBook = toUserBook(book)
      val argsBook = MutationsAddBookArgs(otherCollection.some, book)
      for {
        _ <- collectionService.addBookToCollection(
          MutationsAddBookArgs(hook1Collection.some, book)
        )
        _ <- collectionService.addBookToCollection(
          MutationsAddBookArgs(hook2Collection.some, book)
        )
        _ <- collectionService.addBookToCollection(argsBook)
        hook1Response <-
          collectionService.collection(QueriesCollectionArgs(hook1Collection))
        hook2Response <-
          collectionService.collection(QueriesCollectionArgs(hook2Collection))
      } yield expect(hook1Response.books.contains(userBook)) and expect(
        !hook2Response.books.contains(userBook)
      )
  }

  test("addBookToCollection ignores hook of wrong type") { collectionService =>
    val book     = baseBook.copy(isbn = "isbn3")
    val userBook = toUserBook(book)
    val argsBook = MutationsAddBookArgs(otherCollection.some, book)
    for {
      _ <- collectionService.addBookToCollection(argsBook)
      hook3Response <-
        collectionService.collection(QueriesCollectionArgs(hook3Collection))
    } yield expect(!hook3Response.books.contains(userBook))
  }

  test("addBookToCollection does not create special collection if add false") {
    collectionService =>
      val book     = baseBook.copy(isbn = "isbn4")
      val argsBook = MutationsAddBookArgs(otherCollection.some, book)
      for {
        _ <- collectionService.addBookToCollection(argsBook)
        hookResponse <-
          collectionService
            .collection(
              QueriesCollectionArgs(hookAlwaysFalseCollection)
            )
            .attempt
      } yield expect(
        hookResponse == CollectionDoesNotExistError(
          hookAlwaysFalseCollection
        ).asLeft
      )
  }

  test("addBookToCollection creates special collection if not exists") {
    collectionService =>
      val book     = baseBook.copy(title = "special", isbn = "isbn5")
      val argsBook = MutationsAddBookArgs(otherCollection.some, book)
      for {
        _ <- collectionService.addBookToCollection(argsBook)
        hookResponse <-
          collectionService
            .collection(
              QueriesCollectionArgs(hookNotCreatedCollection)
            )
            .attempt
      } yield expect(hookResponse.isRight)
  }

  test(
    "addBookToCollection adds to default collection when no collection in args"
  ) { collectionService =>
    val book     = baseBook.copy(isbn = "isbn-to-default")
    val argsBook = MutationsAddBookArgs(None, book)
    for {
      _ <- collectionService.addBookToCollection(argsBook)
      hook1Collection <-
        collectionService.collection(QueriesCollectionArgs(hook1Collection))
    } yield expect(hook1Collection.books.map(_.isbn).contains(book.isbn))
  }

  test(
    "addBookToCollection errors when no default collection and no collection in args"
  ) { _ =>
    val stubbedService = SpecialCollectionService(None, null, null, null)
    val book           = baseBook.copy(isbn = "isbn will never be added")
    val argsBook       = MutationsAddBookArgs(None, book)
    for {
      response <- stubbedService.addBookToCollection(argsBook).attempt
    } yield expect(
      response.swap.exists(_ == DefaultCollectionNotSupportedError)
    )
  }

  test("updateCollection errors if name specified for special collection") {
    collectionService =>
      for {
        response <-
          collectionService
            .updateCollection(
              MutationsUpdateCollectionArgs(
                hook1Collection,
                "a new name".some,
                None
              )
            )
            .attempt
      } yield expect(
        response.swap.exists(_ == CannotChangeNameOfSpecialCollectionError)
      )
  }

  test(
    "updateCollection successful if name not specified for special collection"
  ) { collectionService =>
    val newSort = Sort.Author
    for {
      _ <-
        collectionService
          .updateCollection(
            MutationsUpdateCollectionArgs(
              hook1Collection,
              None,
              newSort.some
            )
          )
      hookResponse <-
        collectionService.collection(QueriesCollectionArgs(hook1Collection))
    } yield expect(hookResponse.preferredSort === newSort)
  }

  test("deleteCollection errors if special collection") { collectionService =>
    for {
      response <-
        collectionService
          .deleteCollection(MutationsDeleteCollectionArgs(hook1Collection))
          .attempt
    } yield expect(
      response.swap.exists(_ == CannotDeleteSpecialCollectionError)
    )
  }

  test("deleteCollection succeeds if not special collection") {
    collectionService =>
      val collection = "not a special collection"
      for {
        _ <- collectionService.createCollection(
          MutationsCreateCollectionArgs(collection, None)
        )
        _ <-
          collectionService
            .deleteCollection(MutationsDeleteCollectionArgs(collection))
        collection <-
          collectionService
            .collection(QueriesCollectionArgs(collection))
            .attempt
      } yield expect(collection.isLeft)
  }
}
