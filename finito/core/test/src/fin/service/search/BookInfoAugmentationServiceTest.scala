package fin.service.search

import java.time.LocalDate

import cats.arrow.FunctionK
import cats.effect.{Ref, _}
import cats.implicits._
import weaver._

import fin.BookConversions._
import fin.Types._
import fin.implicits._
import fin.service.book.InMemoryBookRepository

object BookInfoAugmentationServiceTest extends SimpleIOSuite {

  val date     = LocalDate.of(2021, 5, 22)
  val baseBook = BookInput("title", List("author"), "my desc", "isbn", "uri")
  val repo =
    new InMemoryBookRepository[IO](Ref.unsafe[IO, List[UserBook]](List.empty))

  test("search is augemented with data") {
    val book1 = baseBook.copy(isbn = "isbn for search #1")
    val book2 = baseBook.copy(isbn = "isbn for search #2")
    val service =
      BookInfoAugmentationService[IO, IO](
        new MockedInfoService(book2.toUserBook()),
        repo,
        FunctionK.id[IO]
      )
    val rating = 4
    for {
      _        <- repo.createBook(book1, date)
      _        <- repo.createBook(book2, date)
      _        <- repo.rateBook(book2, rating)
      _        <- repo.startReading(book2, date)
      response <- service.search(QueryBooksArgs(None, None, None, None))
    } yield expect(
      response === List(
        book2.toUserBook(
          dateAdded = date.some,
          rating = rating.some,
          startedReading = date.some
        )
      )
    )
  }

  test("fromIsbn is augmented with data") {
    val book = baseBook.copy(isbn = "isbn for fromIsbn")
    val service =
      BookInfoAugmentationService[IO, IO](
        new MockedInfoService(book.toUserBook()),
        repo,
        FunctionK.id[IO]
      )
    val rating = 4
    for {
      _            <- repo.createBook(book, date)
      _            <- repo.rateBook(book, rating)
      _            <- repo.startReading(book, date)
      bookResponse <- service.fromIsbn(QueryBookArgs(book.isbn, None))
    } yield expect(
      bookResponse === List(
        book.toUserBook(
          dateAdded = date.some,
          rating = rating.some,
          startedReading = date.some
        )
      )
    )
  }
}

class MockedInfoService(book: UserBook) extends BookInfoService[IO] {

  override def search(booksArgs: QueryBooksArgs): IO[List[UserBook]] =
    List(book).pure[IO]

  override def fromIsbn(bookArgs: QueryBookArgs): IO[List[UserBook]] =
    List(book).pure[IO]
}
