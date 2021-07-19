package fin.service

import cats.effect.concurrent.Ref
import cats.effect.{Clock, IO, Resource}
import cats.implicits._
import weaver._

import fin.Constants
import fin.Types._
import fin.persistence.BookRepository

object BookManagementServiceImplTest extends IOSuite {

  val book =
    Book(
      "title",
      List("author"),
      "cool description",
      "???",
      "uri",
      Constants.emptyUserData
    )

  override type Res = (BookRepository[IO], BookManagementService[IO])
  override def sharedResource
      : Resource[IO, (BookRepository[IO], BookManagementService[IO])] =
    Resource.eval(Ref.of[IO, List[Book]](List.empty).map { ref =>
      val repo = new InMemoryBookRepository(ref)
      (repo, BookManagementServiceImpl(repo, Clock[IO]))
    })

  test("createBook creates book") {
    case (repo, bookService) =>
      for {
        _         <- bookService.createBook(MutationsCreateBookArgs(book))
        maybeBook <- repo.retrieveBook(book.isbn)
      } yield expect(maybeBook.nonEmpty)
  }

  test("createBook errors if book already exists") {
    case (_, bookService) =>
      val copiedBook = book.copy(isbn = "copied")
      for {
        _ <- bookService.createBook(MutationsCreateBookArgs(copiedBook))
        response <-
          bookService.createBook(MutationsCreateBookArgs(copiedBook)).attempt
      } yield expect(
        response.swap.exists(_ == BookAlreadyExistsError(copiedBook))
      )
  }

  test("rateBook rates book") {
    case (repo, bookService) =>
      val bookToRate = book.copy(isbn = "rate")
      val rating     = 4
      for {
        _         <- bookService.createBook(MutationsCreateBookArgs(bookToRate))
        _         <- bookService.rateBook(MutationsRateBookArgs(bookToRate, rating))
        maybeBook <- repo.retrieveBook(bookToRate.isbn)
      } yield expect(
        maybeBook.exists(
          _ == bookToRate.copy(userData =
            bookToRate.userData.copy(rating = rating.some)
          )
        )
      )
  }

}
