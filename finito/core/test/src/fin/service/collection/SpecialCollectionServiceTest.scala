package fin.service.collection

import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.BookConversions._
import fin.Types._
import fin.implicits._
import fin.{fixtures, _}

object SpecialCollectionServiceTest extends IOSuite {

  val otherCollection           = "other collection"
  val hook1Collection           = "default"
  val hook2Collection           = "cool"
  val hook3Collection           = "my ratings"
  val hookNotCreatedCollection  = "uncreated"
  val hookAlwaysFalseCollection = "always false"
  val specialCollections = List(
    SpecialCollection(
      hook1Collection,
      false.some,
      "add = true".some,
      None,
      None,
      None,
      None
    ),
    SpecialCollection(
      hook2Collection,
      false.some,
      "if string.find(title, \"cool\") then add = true else remove = true end".some,
      None,
      None,
      None,
      None
    ),
    SpecialCollection(
      hook3Collection,
      false.some,
      None,
      None,
      None,
      "add = true".some,
      None
    ),
    SpecialCollection(
      hookNotCreatedCollection,
      true.some,
      "if title == \"special\" then add = true end".some,
      None,
      None,
      None,
      None
    ),
    SpecialCollection(
      hookAlwaysFalseCollection,
      true.some,
      "add = false".some,
      None,
      None,
      None,
      None
    ),
    SpecialCollection(
      otherCollection,
      false.some,
      None,
      None,
      None,
      None,
      None
    )
  )

  implicit def unsafeLogger: Logger[IO] = Slf4jLogger.getLogger

  override type Res = CollectionService[IO]
  override def sharedResource: Resource[IO, CollectionService[IO]] =
    for {
      colRef <- Resource.eval(Ref.of[IO, List[Collection]](List.empty))
      wrappedCollectionService = CollectionServiceImpl[IO, IO](
        new InMemoryCollectionRepository(colRef),
        Clock[IO],
        FunctionK.id[IO]
      )
      hookExecutionService = HookExecutionServiceImpl[IO]
      specialCollectionService = SpecialCollectionService(
        hook1Collection.some,
        wrappedCollectionService,
        specialCollections,
        hookExecutionService
      )
      _ <- specialCollections.filter(_.`lazy`.contains(false)).traverse {
        collection =>
          Resource.eval(
            wrappedCollectionService.createCollection(
              MutationCreateCollectionArgs(
                collection.name,
                None,
                collection.preferredSort.map(_.`type`),
                collection.preferredSort.map(_.sortAscending)
              )
            )
          )
      }
    } yield specialCollectionService

  test("addBookToCollection adds for matching hook, but not for others") {
    collectionService =>
      val book     = fixtures.bookInput.copy(isbn = "isbn1")
      val argsBook = MutationAddBookArgs(otherCollection.some, book)
      for {
        _ <- collectionService.addBookToCollection(argsBook)
        hook1Response <- collectionService.collection(
          QueryCollectionArgs(hook1Collection, None)
        )
        hook2Response <- collectionService.collection(
          QueryCollectionArgs(hook2Collection, None)
        )
      } yield expect(hook1Response.books.contains(toUserBook(book))) and expect(
        !hook2Response.books.contains(toUserBook(book))
      )
  }

  test("addBookToCollection removes for matching hook, but not for others") {
    collectionService =>
      val book     = fixtures.bookInput.copy(isbn = "isbn2")
      val userBook = toUserBook(book)
      val argsBook = MutationAddBookArgs(otherCollection.some, book)
      for {
        _ <- collectionService.addBookToCollection(
          MutationAddBookArgs(hook1Collection.some, book)
        )
        _ <- collectionService.addBookToCollection(
          MutationAddBookArgs(hook2Collection.some, book)
        )
        _ <- collectionService.addBookToCollection(argsBook)
        hook1Response <- collectionService.collection(
          QueryCollectionArgs(hook1Collection, None)
        )
        hook2Response <- collectionService.collection(
          QueryCollectionArgs(hook2Collection, None)
        )
      } yield expect(hook1Response.books.contains(userBook)) and expect(
        !hook2Response.books.contains(userBook)
      )
  }

  test("addBookToCollection ignores hook of wrong type") { collectionService =>
    val book     = fixtures.bookInput.copy(isbn = "isbn3")
    val userBook = toUserBook(book)
    val argsBook = MutationAddBookArgs(otherCollection.some, book)
    for {
      _ <- collectionService.addBookToCollection(argsBook)
      hook3Response <- collectionService.collection(
        QueryCollectionArgs(hook3Collection, None)
      )
    } yield expect(!hook3Response.books.contains(userBook))
  }

  test("addBookToCollection does not create special collection if add false") {
    collectionService =>
      val book     = fixtures.bookInput.copy(isbn = "isbn4")
      val argsBook = MutationAddBookArgs(otherCollection.some, book)
      for {
        _ <- collectionService.addBookToCollection(argsBook)
        hookResponse <-
          collectionService
            .collection(
              QueryCollectionArgs(hookAlwaysFalseCollection, None)
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
      val book     = fixtures.bookInput.copy(title = "special", isbn = "isbn5")
      val argsBook = MutationAddBookArgs(otherCollection.some, book)
      for {
        _ <- collectionService.addBookToCollection(argsBook)
        hookResponse <-
          collectionService
            .collection(
              QueryCollectionArgs(hookNotCreatedCollection, None)
            )
            .attempt
      } yield expect(hookResponse.isRight)
  }

  test(
    "addBookToCollection adds to default collection when no collection in args"
  ) { collectionService =>
    val book     = fixtures.bookInput.copy(isbn = "isbn-to-default")
    val argsBook = MutationAddBookArgs(None, book)
    for {
      _ <- collectionService.addBookToCollection(argsBook)
      hook1Collection <- collectionService.collection(
        QueryCollectionArgs(hook1Collection, None)
      )
    } yield expect(hook1Collection.books.map(_.isbn).contains(book.isbn))
  }

  test(
    "addBookToCollection errors when no default collection and no collection in args"
  ) { collectionService =>
    val stubbedService = SpecialCollectionService(
      None,
      collectionService,
      List.empty,
      HookExecutionServiceImpl[IO]
    )
    val book     = fixtures.bookInput.copy(isbn = "isbn will never be added")
    val argsBook = MutationAddBookArgs(None, book)
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
              MutationUpdateCollectionArgs(
                hook1Collection,
                "a new name".some,
                None,
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
    val newSort = Sort(SortType.Author, true)
    for {
      _ <-
        collectionService
          .updateCollection(
            MutationUpdateCollectionArgs(
              hook1Collection,
              None,
              newSort.`type`.some,
              newSort.sortAscending.some
            )
          )
      hookResponse <- collectionService.collection(
        QueryCollectionArgs(hook1Collection, None)
      )
    } yield expect(hookResponse.preferredSort === newSort)
  }

  test("deleteCollection errors if special collection") { collectionService =>
    for {
      response <-
        collectionService
          .deleteCollection(MutationDeleteCollectionArgs(hook1Collection))
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
          MutationCreateCollectionArgs(collection, None, None, None)
        )
        _ <-
          collectionService
            .deleteCollection(MutationDeleteCollectionArgs(collection))
        collection <-
          collectionService
            .collection(QueryCollectionArgs(collection, None))
            .attempt
      } yield expect(collection.isLeft)
  }
}
