package fin.service.book

import cats.effect._
import cats.implicits._

import fin.Types._
import fin.service.search.BookInfoService

class BookInfoServiceUsingTitles(books: List[UserBook])
    extends BookInfoService[IO] {

  override def search(booksArgs: QueryBooksArgs): IO[List[UserBook]] =
    books.filter(b => booksArgs.titleKeywords.exists(_ === b.title)).pure[IO]

  override def fromIsbn(bookArgs: QueryBookArgs): IO[List[UserBook]] = ???
}
