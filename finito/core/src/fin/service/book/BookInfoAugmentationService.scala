package fin.service.book

import cats.effect._
import cats.implicits._
import cats.~>

import fin.Types._
import fin.persistence.BookRepository

class BookInfoAugmentationService[F[_]: BracketThrow, G[_]] private (
    wrappedInfoService: BookInfoService[F],
    bookRepo: BookRepository[G],
    transact: G ~> F
) extends BookInfoService[F] {

  override def search(
      booksArgs: QueriesBooksArgs
  ): F[List[UserBook]] =
    for {
      searchResult <- wrappedInfoService.search(booksArgs)
      userBooks <- transact(
        bookRepo.retrieveMultipleBooks(searchResult.map(_.isbn))
      )
    } yield searchResult.map(book =>
      userBooks.find(_.isbn === book.isbn).getOrElse(book)
    )

  override def fromIsbn(bookArgs: QueriesBookArgs): F[List[UserBook]] =
    for {
      matchingBooks <- wrappedInfoService.fromIsbn(bookArgs)
      userBooks <- transact(
        bookRepo.retrieveMultipleBooks(matchingBooks.map(_.isbn))
      )
    } yield matchingBooks.map(book =>
      userBooks.find(_.isbn === book.isbn).getOrElse(book)
    )
}

object BookInfoAugmentationService {
  def apply[F[_]: BracketThrow, G[_]](
      wrappedInfoService: BookInfoService[F],
      bookRepo: BookRepository[G],
      transact: G ~> F
  ) =
    new BookInfoAugmentationService[F, G](
      wrappedInfoService,
      bookRepo,
      transact
    )
}
