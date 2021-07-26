package fin.service

import fin.Types._

trait BookManagementService[F[_]] {
  def createBook(args: MutationsCreateBookArgs): F[UserBook]
  def rateBook(args: MutationsRateBookArgs): F[UserBook]
  def startReading(args: MutationsStartReadingArgs): F[UserBook]
  def finishReading(args: MutationsFinishReadingArgs): F[UserBook]
  def deleteBookData(args: MutationsDeleteBookDataArgs): F[Unit]
}
