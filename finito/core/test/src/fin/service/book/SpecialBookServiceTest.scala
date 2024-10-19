package fin.service.book

import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.BookConversions._
import fin.Types._
import fin.fixtures
import fin.service.collection._

object SpecialBookServiceTest extends IOSuite {

  val triggerRating = 1337

  val onRateHookCollection      = "rated books collection"
  val onStartHookCollection     = "started books collection"
  val onFinishHookCollection    = "finished books collection"
  val hookAlwaysFalseCollection = "hook evals to always false"
  val lazyCollection            = "lazy collection"
  val specialCollections = List(
    SpecialCollection(
      onRateHookCollection,
      false.some,
      None,
      None,
      None,
      "if rating >= 5 then add = true else remove = true end".some,
      None
    ),
    SpecialCollection(
      onStartHookCollection,
      false.some,
      None,
      "add = true".some,
      None,
      None,
      None
    ),
    SpecialCollection(
      onFinishHookCollection,
      false.some,
      None,
      None,
      "add = true".some,
      None,
      None
    ),
    SpecialCollection(
      hookAlwaysFalseCollection,
      false.some,
      "add = false".some,
      "add = false".some,
      "add = false".some,
      "add = false".some,
      None
    ),
    SpecialCollection(
      lazyCollection,
      true.some,
      None,
      None,
      None,
      show"if rating == $triggerRating then add = true else add = false end".some,
      None
    )
  )

  implicit def unsafeLogger: Logger[IO] = Slf4jLogger.getLogger

  override type Res = (CollectionService[IO], BookManagementService[IO])
  override def sharedResource
      : Resource[IO, (CollectionService[IO], BookManagementService[IO])] =
    for {
      colRef <- Resource.eval(Ref.of[IO, List[Collection]](List.empty))
      wrappedCollectionService = CollectionServiceImpl[IO, IO](
        new InMemoryCollectionRepository(colRef),
        Clock[IO],
        FunctionK.id[IO]
      )
      bookRef <- Resource.eval(Ref.of[IO, List[UserBook]](List.empty))
      wrappedBookService = BookManagementServiceImpl[IO, IO](
        new InMemoryBookRepository[IO](bookRef),
        Clock[IO],
        FunctionK.id[IO]
      )
      hookExecutionService = HookExecutionServiceImpl[IO]
      specialBookService = SpecialBookService[IO](
        wrappedCollectionService,
        wrappedBookService,
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
    } yield (wrappedCollectionService, specialBookService)

  test("rateBook adds for matching hook, but not for others") {
    case (collectionService, bookService) =>
      val book     = fixtures.bookInput.copy(isbn = "book to rate")
      val rateArgs = MutationRateBookArgs(book, 5)
      for {
        _ <- bookService.rateBook(rateArgs)
        rateHookResponse <- collectionService.collection(
          QueryCollectionArgs(onRateHookCollection, None)
        )
        alwaysFalseHookResponse <- collectionService.collection(
          QueryCollectionArgs(hookAlwaysFalseCollection, None)
        )
      } yield expect(
        rateHookResponse.books.contains(book.toUserBook())
      ) and expect(
        !alwaysFalseHookResponse.books.contains(book.toUserBook())
      )
  }

  test("startReading adds for matching hook, but not for others") {
    case (collectionService, bookService) =>
      val book = fixtures.bookInput.copy(isbn = "book to start reading")
      val startReadingArgs = MutationStartReadingArgs(book, None)
      for {
        _ <- bookService.startReading(startReadingArgs)
        startReadingHookResponse <- collectionService.collection(
          QueryCollectionArgs(onStartHookCollection, None)
        )
        alwaysFalseHookResponse <- collectionService.collection(
          QueryCollectionArgs(hookAlwaysFalseCollection, None)
        )
      } yield expect(
        startReadingHookResponse.books.contains(book.toUserBook())
      ) and expect(
        !alwaysFalseHookResponse.books.contains(book.toUserBook())
      )
  }

  test("finishReading adds for matching hook, but not for others") {
    case (collectionService, bookService) =>
      val book = fixtures.bookInput.copy(isbn = "book to finish reading")
      val finishReadingArgs = MutationFinishReadingArgs(book, None)
      for {
        _ <- bookService.finishReading(finishReadingArgs)
        finishReadingHookResponse <- collectionService.collection(
          QueryCollectionArgs(onFinishHookCollection, None)
        )
        alwaysFalseHookResponse <- collectionService.collection(
          QueryCollectionArgs(hookAlwaysFalseCollection, None)
        )
      } yield expect(
        finishReadingHookResponse.books.contains(book.toUserBook())
      ) and expect(
        !alwaysFalseHookResponse.books.contains(book.toUserBook())
      )
  }

  test("rateBook creates collection if not exists") {
    case (collectionService, bookService) =>
      val book     = fixtures.bookInput.copy(isbn = "book to trigger creation")
      val rateArgs = MutationRateBookArgs(book, triggerRating)
      for {
        _ <- bookService.rateBook(rateArgs)
        rateHookResponse <- collectionService.collection(
          QueryCollectionArgs(lazyCollection, None)
        )
      } yield expect(rateHookResponse.books.contains(book.toUserBook()))
  }

  test("rateBook silent error if add to special collection fails") {
    case (collectionService, bookService) =>
      val book     = fixtures.bookInput.copy(isbn = "book to rate twice")
      val rateArgs = MutationRateBookArgs(book, 5)
      for {
        _ <- bookService.rateBook(rateArgs)
        // We should get a failure here by adding it again
        _ <- bookService.rateBook(rateArgs.copy(rating = 6))
        rateHookResponse <- collectionService.collection(
          QueryCollectionArgs(onRateHookCollection, None)
        )
      } yield expect(rateHookResponse.books.contains(book.toUserBook()))
  }

  test("rateBook removes from collection") {
    case (collectionService, bookService) =>
      val book = fixtures.bookInput.copy(isbn = "book to rate good then bad")
      val rateArgs = MutationRateBookArgs(book, 5)
      for {
        _ <- bookService.rateBook(rateArgs)
        _ <- bookService.rateBook(rateArgs.copy(rating = 2))
        rateHookResponse <- collectionService.collection(
          QueryCollectionArgs(onRateHookCollection, None)
        )
      } yield expect(!rateHookResponse.books.contains(book.toUserBook()))
  }
}
