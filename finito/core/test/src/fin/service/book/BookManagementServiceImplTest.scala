package fin.service.book

import java.time.LocalDate

import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import weaver._

import fin.BookConversions._
import fin.Types._
import fin.implicits._
import fin.{fixtures, _}

object BookManagementServiceImplTest extends IOSuite {

  override type Res = BookManagementService[IO]
  override def sharedResource: Resource[IO, BookManagementService[IO]] =
    Resource.eval(Ref.of[IO, List[UserBook]](List.empty).map { ref =>
      val repo = new InMemoryBookRepository(ref)
      BookManagementServiceImpl[IO, IO](
        repo,
        fixtures.clock,
        FunctionK.id[IO]
      )
    })

  test("createBook creates book") {
    case bookService =>
      for {
        _ <- bookService.createBook(MutationsCreateBookArgs(fixtures.bookInput))
      } yield success
  }

  test("createBook errors if book already exists") { bookService =>
    val copiedBook = fixtures.bookInput.copy(isbn = "copied")
    for {
      _ <- bookService.createBook(MutationsCreateBookArgs(copiedBook))
      response <-
        bookService.createBook(MutationsCreateBookArgs(copiedBook)).attempt
    } yield expect(
      response.swap.exists(_ == BookAlreadyExistsError(copiedBook))
    )
  }

  test("rateBook rates book") { bookService =>
    val bookToRate = fixtures.bookInput.copy(isbn = "rate")
    val rating     = 4
    for {
      _ <- bookService.createBook(MutationsCreateBookArgs(bookToRate))
      ratedBook <-
        bookService.rateBook(MutationsRateBookArgs(bookToRate, rating))
    } yield expect(
      ratedBook === toUserBook(
        bookToRate,
        rating = rating.some,
        dateAdded = fixtures.date.some
      )
    )
  }

  test("rateBook creates book if not exists") { bookService =>
    val bookToRate = fixtures.bookInput.copy(isbn = "rate no book")
    val rating     = 4
    for {
      ratedBook <-
        bookService.rateBook(MutationsRateBookArgs(bookToRate, rating))
    } yield expect(
      ratedBook === toUserBook(
        bookToRate,
        dateAdded = fixtures.date.some,
        rating = rating.some
      )
    )
  }

  test("addBookReview adds review to book") { bookService =>
    val bookToReview = fixtures.bookInput.copy(isbn = "review")
    val review       = "Excellent book"
    for {
      _ <- bookService.createBook(MutationsCreateBookArgs(bookToReview))
      reviewdBook <- bookService.addBookReview(
        MutationsAddBookReviewArgs(bookToReview, review)
      )
    } yield expect(
      reviewdBook === toUserBook(
        bookToReview,
        review = review.some,
        dateAdded = fixtures.date.some
      )
    )
  }

  test("addBookReview creates book if not exists") { bookService =>
    val bookToReview = fixtures.bookInput.copy(isbn = "review with no book")
    val review       = "Very excellent book"
    for {
      reviewdBook <- bookService.addBookReview(
        MutationsAddBookReviewArgs(bookToReview, review)
      )
    } yield expect(
      reviewdBook === toUserBook(
        bookToReview,
        dateAdded = fixtures.date.some,
        review = review.some
      )
    )
  }

  test("startReading starts reading") { bookService =>
    val bookToRead     = fixtures.bookInput.copy(isbn = "read")
    val startedReading = LocalDate.parse("2018-11-30")
    for {
      _ <- bookService.createBook(MutationsCreateBookArgs(bookToRead))
      updatedBook <- bookService.startReading(
        MutationsStartReadingArgs(bookToRead, startedReading.some)
      )
    } yield expect(
      updatedBook === toUserBook(
        bookToRead,
        dateAdded = fixtures.date.some,
        startedReading = startedReading.some
      )
    )
  }

  test("startReading gets time from clock if not specified in args") {
    bookService =>
      val bookToRead = fixtures.bookInput.copy(isbn = "read no date")
      for {
        _ <- bookService.createBook(MutationsCreateBookArgs(bookToRead))
        updatedBook <-
          bookService.startReading(MutationsStartReadingArgs(bookToRead, None))
      } yield expect(
        updatedBook === toUserBook(
          bookToRead,
          dateAdded = fixtures.date.some,
          startedReading = fixtures.date.some
        )
      )
  }

  test("startReading errors if already reading") { bookService =>
    val copiedBook = fixtures.bookInput.copy(isbn = "copied reading")
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
    val popularBook = fixtures.bookInput.copy(isbn = "popular")
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
        dateAdded = fixtures.date.some,
        startedReading = fixtures.date.some,
        lastRead = fixtures.date.some
      )
    )
  }

  test("finishReading finishes reading") { bookService =>
    val bookToRead      = fixtures.bookInput.copy(isbn = "finished")
    val finishedReading = LocalDate.parse("2018-11-30")
    for {
      _ <- bookService.createBook(MutationsCreateBookArgs(bookToRead))
      updatedBook <- bookService.finishReading(
        MutationsFinishReadingArgs(bookToRead, finishedReading.some)
      )
    } yield expect(
      updatedBook === toUserBook(
        bookToRead,
        lastRead = finishedReading.some,
        dateAdded = fixtures.date.some
      )
    )
  }

  test("finishReading time from clock if not specified in args") {
    bookService =>
      val bookToRead = fixtures.bookInput.copy(isbn = "finished no date")
      for {
        _ <- bookService.createBook(MutationsCreateBookArgs(bookToRead))
        updatedBook <- bookService.finishReading(
          MutationsFinishReadingArgs(bookToRead, None)
        )
      } yield expect(
        updatedBook === toUserBook(
          bookToRead,
          lastRead = fixtures.date.some,
          dateAdded = fixtures.date.some
        )
      )
  }

  test("deleteBookData deletes book data") { _ =>
    val bookToClear     = fixtures.bookInput.copy(isbn = "book to delete data from")
    val finishedReading = LocalDate.parse("2018-11-30")
    val bookRef         = Ref.unsafe[IO, List[UserBook]](List.empty)
    val repo            = new InMemoryBookRepository(bookRef)
    val service =
      BookManagementServiceImpl[IO, IO](
        repo,
        fixtures.clock,
        FunctionK.id[IO]
      )

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
    } yield expect(
      book.exists(_ == toUserBook(bookToClear, dateAdded = fixtures.date.some))
    )
  }
}
