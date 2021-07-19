package fin.service

import java.time.Instant

import scala.concurrent.duration.{MILLISECONDS, TimeUnit}

import cats.Applicative
import cats.effect.concurrent.Ref
import cats.effect.{Clock, IO, Resource}
import cats.implicits._
import weaver._

import fin.BookConversions._
import fin.Types._
import fin.implicits._

object BookManagementServiceImplTest extends IOSuite {

  val constantTime = Instant.parse("2021-11-30T00:00:00.00Z")

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
      BookManagementServiceImpl(
        repo,
        TestClock[IO](constantTime.toEpochMilli)
      )
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
    val startedReading = Instant.parse("2018-11-30T00:00:00.00Z")
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

  test("finishReading finishes reading") { bookService =>
    val bookToRead      = book.copy(isbn = "finished")
    val finishedReading = Instant.parse("2018-11-30T00:00:00.00Z")
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
}

/**
  * A Test clock that always returns a constant time.
  *
  * @param epoch the constant time as a unix epoch
  */
case class TestClock[F[_]: Applicative](epoch: Long) extends Clock[F] {

  override def realTime(unit: TimeUnit): F[Long] =
    unit.convert(epoch, MILLISECONDS).pure[F]

  override def monotonic(unit: TimeUnit): F[Long] =
    unit.convert(epoch, MILLISECONDS).pure[F]
}
