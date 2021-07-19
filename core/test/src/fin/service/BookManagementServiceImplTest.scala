package fin.service

import cats.effect.concurrent.Ref
import cats.effect.{Clock, IO, Resource}
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

}
