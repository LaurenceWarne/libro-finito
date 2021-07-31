package fin.service.book

import cats.Monad
import cats.implicits._

import fin.Types._
import fin.persistence.BookRepository

class BookInfoAugmentationService[F[_]: Monad] private (
    wrappedInfoService: BookInfoService[F],
    bookRepo: BookRepository[F]
) extends BookInfoService[F] {

  override def search(
      booksArgs: QueriesBooksArgs
  ): F[List[UserBook]] =
    for {
      searchResult <- wrappedInfoService.search(booksArgs)
      userBooks    <- bookRepo.retrieveMultipleBooks(searchResult.map(_.isbn))
    } yield searchResult.map(book =>
      userBooks.find(_.isbn === book.isbn).getOrElse(book)
    )

  override def fromIsbn(bookArgs: QueriesBookArgs): F[UserBook] =
    for {
      book     <- wrappedInfoService.fromIsbn(bookArgs)
      userBook <- bookRepo.retrieveBook(bookArgs.isbn)
    } yield userBook.getOrElse(book)
}

object BookInfoAugmentationService {
  def apply[F[_]: Monad](
      wrappedInfoService: BookInfoService[F],
      bookRepo: BookRepository[F]
  ): BookInfoAugmentationService[F] =
    new BookInfoAugmentationService(wrappedInfoService, bookRepo)
}
