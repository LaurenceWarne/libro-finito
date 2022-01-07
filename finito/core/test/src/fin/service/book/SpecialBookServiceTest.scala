package fin.service.book

import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.BookConversions._
import fin.Types._
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

  val baseBook =
    BookInput(
      "title",
      List("auth"),
      "description",
      "isbn",
      "thumbnail uri"
    )
  val book2 = baseBook.copy(title = "my cool book")

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
              MutationsCreateCollectionArgs(
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
      val book     = baseBook.copy(isbn = "book to rate")
      val rateArgs = MutationsRateBookArgs(book, 5)
      for {
        _ <- bookService.rateBook(rateArgs)
        rateHookResponse <- collectionService.collection(
          QueriesCollectionArgs(onRateHookCollection)
        )
        alwaysFalseHookResponse <- collectionService.collection(
          QueriesCollectionArgs(hookAlwaysFalseCollection)
        )
      } yield expect(
        rateHookResponse.books.contains(toUserBook(book))
      ) and expect(
        !alwaysFalseHookResponse.books.contains(toUserBook(book))
      )
  }

  test("startReading adds for matching hook, but not for others") {
    case (collectionService, bookService) =>
      val book             = baseBook.copy(isbn = "book to start reading")
      val startReadingArgs = MutationsStartReadingArgs(book, None)
      for {
        _ <- bookService.startReading(startReadingArgs)
        startReadingHookResponse <- collectionService.collection(
          QueriesCollectionArgs(onStartHookCollection)
        )
        alwaysFalseHookResponse <- collectionService.collection(
          QueriesCollectionArgs(hookAlwaysFalseCollection)
        )
      } yield expect(
        startReadingHookResponse.books.contains(toUserBook(book))
      ) and expect(
        !alwaysFalseHookResponse.books.contains(toUserBook(book))
      )
  }

  test("finishReading adds for matching hook, but not for others") {
    case (collectionService, bookService) =>
      val book              = baseBook.copy(isbn = "book to finish reading")
      val finishReadingArgs = MutationsFinishReadingArgs(book, None)
      for {
        _ <- bookService.finishReading(finishReadingArgs)
        finishReadingHookResponse <- collectionService.collection(
          QueriesCollectionArgs(onFinishHookCollection)
        )
        alwaysFalseHookResponse <- collectionService.collection(
          QueriesCollectionArgs(hookAlwaysFalseCollection)
        )
      } yield expect(
        finishReadingHookResponse.books.contains(toUserBook(book))
      ) and expect(
        !alwaysFalseHookResponse.books.contains(toUserBook(book))
      )
  }

  test("rateBook creates collection if not exists") {
    case (collectionService, bookService) =>
      val book     = baseBook.copy(isbn = "book to trigger creation")
      val rateArgs = MutationsRateBookArgs(book, triggerRating)
      for {
        _ <- bookService.rateBook(rateArgs)
        rateHookResponse <- collectionService.collection(
          QueriesCollectionArgs(lazyCollection)
        )
      } yield expect(rateHookResponse.books.contains(toUserBook(book)))
  }

  test("rateBook silent error if add to special collection fails") {
    case (collectionService, bookService) =>
      val book     = baseBook.copy(isbn = "book to rate twice")
      val rateArgs = MutationsRateBookArgs(book, 5)
      for {
        _ <- bookService.rateBook(rateArgs)
        // We should get a failure here by adding it again
        _ <- bookService.rateBook(rateArgs.copy(rating = 6))
        rateHookResponse <- collectionService.collection(
          QueriesCollectionArgs(onRateHookCollection)
        )
      } yield expect(rateHookResponse.books.contains(toUserBook(book)))
  }

  test("rateBook removes from collection") {
    case (collectionService, bookService) =>
      val book     = baseBook.copy(isbn = "book to rate good then bad")
      val rateArgs = MutationsRateBookArgs(book, 5)
      for {
        _ <- bookService.rateBook(rateArgs)
        _ <- bookService.rateBook(rateArgs.copy(rating = 2))
        rateHookResponse <- collectionService.collection(
          QueriesCollectionArgs(onRateHookCollection)
        )
      } yield expect(!rateHookResponse.books.contains(toUserBook(book)))
  }
}
