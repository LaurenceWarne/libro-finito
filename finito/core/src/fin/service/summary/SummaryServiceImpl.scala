package fin.service.summary

import cats.effect._
import cats.implicits._
import cats.~>

import fin.Types._
import fin.persistence.{BookRepository, Dates}

class SummaryServiceImpl[F[_]: Async, G[_]] private (
    bookRepo: BookRepository[G],
    montageService: MontageService[F],
    clock: Clock[F],
    transact: G ~> F
) extends SummaryService[F] {

  override def summary(args: QueriesSummaryArgs): F[Summary] =
    for {
      currentDate <- Async[F].memoize(Dates.currentDate(clock))
      from        <- args.from.fold(currentDate.map(_.withDayOfYear(1)))(_.pure[F])
      to          <- args.to.fold(currentDate)(_.pure[F])
      books       <- transact(bookRepo.retrieveBooksInside(from, to))
      read      = books.filter(_.lastRead.nonEmpty).length
      ratingAvg = mean(books.flatMap(_.rating.toList))
      montage <- montageService.montage(books, args.montageInput)
    } yield Summary(read, books.length, ratingAvg, montage)

  private def mean(ls: List[Int]): Float =
    if (ls.isEmpty) 0f else (ls.sum / ls.size).toFloat
}

object SummaryServiceImpl {
  def apply[F[_]: Async, G[_]](
      bookRepo: BookRepository[G],
      montageService: MontageService[F],
      clock: Clock[F],
      transact: G ~> F
  ) =
    new SummaryServiceImpl[F, G](
      bookRepo,
      montageService,
      clock,
      transact
    )
}
