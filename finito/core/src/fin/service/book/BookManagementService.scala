package fin.service.book

import fin.Types._

trait BookManagementService[F[_]] {
  def books: F[List[UserBook]]
  def createBook(args: MutationCreateBookArgs): F[UserBook]
  def createBooks(books: List[UserBook]): F[List[UserBook]]
  def rateBook(args: MutationRateBookArgs): F[UserBook]
  def addBookReview(args: MutationAddBookReviewArgs): F[UserBook]
  def startReading(args: MutationStartReadingArgs): F[UserBook]
  def finishReading(args: MutationFinishReadingArgs): F[UserBook]
  def deleteBookData(args: MutationDeleteBookDataArgs): F[Unit]
}
