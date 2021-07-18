package fin.service

import fin.Types._

trait BookManagementService[F[_]] {
  def createBook(args: MutationsCreateBookArgs): F[Unit]
  def rateBook(args: MutationsRateBookArgs): F[Book]
  def startReading(args: MutationsStartReadingArgs): F[Book]
  def finishReading(args: MutationsFinishReadingArgs): F[Book]
}
