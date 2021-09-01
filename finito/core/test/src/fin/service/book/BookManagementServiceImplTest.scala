package fin.service.book

import java.time.{LocalDate, ZoneId}

import scala.concurrent.duration.{MILLISECONDS}

import cats.Applicative
import cats.arrow.FunctionK
import cats.effect.Ref
import cats.effect.{Clock, IO, Resource}
import cats.implicits._
import weaver._

import fin.BookConversions._
import fin.Types._
import fin._
import fin.implicits._
import scala.concurrent.duration.FiniteDuration

object BookManagementServiceImplTest extends IOSuite {

  val constantTime = LocalDate.parse("2021-11-30")
  val testClock = TestClock[IO](
    constantTime
      .atStartOfDay(ZoneId.systemDefault())
      .toEpochSecond * 1000L
  )

  val book =
    BookInput(
      "title",
      List("author"),
      "cool description",
      "???",
      "uri"
    )

  override type Res = BookManagementService[IO]
  override def sharedResource: Resource[IO, BookManagementService[IO]] =
    Resource.eval(Ref.of[IO, List[UserBook]](List.empty).map { ref =>
      val repo = new InMemoryBookRepository(ref)
      BookManagementServiceImpl[IO, IO](repo, testClock, FunctionK.id[IO])
    })

  test("createBook creates book") {
    case bookService =>
      for {
        _ <- bookService.createBook(MutationsCreateBookArgs(book))
      } yield success
  }

  test("createBook errors if book already exists") { bookService =>
    val copiedBook = book.copy(isbn = "copied")
    for {
      _ <- bookService.createBook(MutationsCreateBookArgs(copiedBook))
      response <-
        bookService.createBook(MutationsCreateBookArgs(copiedBook)).attempt
    } yield expect(
      response.swap.exists(_ == BookAlreadyExistsError(copiedBook))
    )
  }

  test("rateBook rates book") { bookService =>
    val bookToRate = book.copy(isbn = "rate")
    val rating     = 4
    for {
      _ <- bookService.createBook(MutationsCreateBookArgs(bookToRate))
      ratedBook <-
        bookService.rateBook(MutationsRateBookArgs(bookToRate, rating))
    } yield expect(ratedBook === toUserBook(bookToRate, rating = rating.some))
  }

  test("rateBook creates book if not exists") { bookService =>
    val bookToRate = book.copy(isbn = "rate no book")
    val rating     = 4
    for {
      ratedBook <-
        bookService.rateBook(MutationsRateBookArgs(bookToRate, rating))
    } yield expect(ratedBook === toUserBook(bookToRate, rating = rating.some))
  }

  test("startReading starts reading") { bookService =>
    val bookToRead     = book.copy(isbn = "read")
    val startedReading = LocalDate.parse("2018-11-30")
    for {
      _ <- bookService.createBook(MutationsCreateBookArgs(bookToRead))
      updatedBook <- bookService.startReading(
        MutationsStartReadingArgs(bookToRead, startedReading.some)
      )
    } yield expect(
      updatedBook === toUserBook(
        bookToRead,
        startedReading = startedReading.some
      )
    )
  }

  test("startReading gets time from clock if not specified in args") {
    bookService =>
      val bookToRead = book.copy(isbn = "read no date")
      for {
        _ <- bookService.createBook(MutationsCreateBookArgs(bookToRead))
        updatedBook <-
          bookService.startReading(MutationsStartReadingArgs(bookToRead, None))
      } yield expect(
        updatedBook === toUserBook(
          bookToRead,
          startedReading = constantTime.some
        )
      )
  }

  test("startReading errors if already reading") { bookService =>
    val copiedBook = book.copy(isbn = "copied reading")
    for {
      _ <- bookService.createBook(MutationsCreateBookArgs(copiedBook))
      _ <- bookService.startReading(MutationsStartReadingArgs(copiedBook, None))
      response <-
        bookService
          .startReading(MutationsStartReadingArgs(copiedBook, None))
          .attempt
    } yield expect(
      response.swap.exists(_ == BookAlreadyBeingReadError(copiedBook))
    )
  }

  test("startReading returns lastRead info when applicable") { bookService =>
    val popularBook = book.copy(isbn = "popular")
    for {
      _ <- bookService.createBook(MutationsCreateBookArgs(popularBook))
      _ <- bookService.finishReading(
        MutationsFinishReadingArgs(popularBook, None)
      )
      book <-
        bookService.startReading(MutationsStartReadingArgs(popularBook, None))
    } yield expect(
      book === toUserBook(
        popularBook,
        startedReading = constantTime.some,
        lastRead = constantTime.some
      )
    )
  }

  test("finishReading finishes reading") { bookService =>
    val bookToRead      = book.copy(isbn = "finished")
    val finishedReading = LocalDate.parse("2018-11-30")
    for {
      _ <- bookService.createBook(MutationsCreateBookArgs(bookToRead))
      updatedBook <- bookService.finishReading(
        MutationsFinishReadingArgs(bookToRead, finishedReading.some)
      )
    } yield expect(
      updatedBook === toUserBook(bookToRead, lastRead = finishedReading.some)
    )
  }

  test("finishReading time from clock if not specified in args") {
    bookService =>
      val bookToRead = book.copy(isbn = "finished no date")
      for {
        _ <- bookService.createBook(MutationsCreateBookArgs(bookToRead))
        updatedBook <- bookService.finishReading(
          MutationsFinishReadingArgs(bookToRead, None)
        )
      } yield expect(
        updatedBook === toUserBook(bookToRead, lastRead = constantTime.some)
      )
  }

  test("deleteBookData deletes book data") { _ =>
    val bookToClear     = book.copy(isbn = "book to delete data from")
    val finishedReading = LocalDate.parse("2018-11-30")
    val bookRef         = Ref.unsafe[IO, List[UserBook]](List.empty)
    val repo            = new InMemoryBookRepository(bookRef)
    val service =
      BookManagementServiceImpl[IO, IO](repo, testClock, FunctionK.id[IO])

    for {
      _ <- service.finishReading(
        MutationsFinishReadingArgs(bookToClear, finishedReading.some)
      )
      _ <- service.startReading(MutationsStartReadingArgs(bookToClear, None))
      _ <- service.rateBook(MutationsRateBookArgs(bookToClear, 3))
      _ <- service.deleteBookData(
        MutationsDeleteBookDataArgs(bookToClear.isbn)
      )
      book <- repo.retrieveBook(bookToClear.isbn)
    } yield expect(book.exists(_ == toUserBook(bookToClear)))
  }
}

/**
  * A Test clock that always returns a constant time.
  *
  * @param epoch the constant time as a unix epoch
  */
final case class TestClock[F[_]: Applicative](epoch: Long) extends Clock[F] {

  override def applicative = implicitly[Applicative[F]]

  override def realTime: F[FiniteDuration] =
    FiniteDuration(epoch, MILLISECONDS).pure[F]

  override def monotonic: F[FiniteDuration] =
    FiniteDuration(epoch, MILLISECONDS).pure[F]
}
