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

  override def summary(args: QuerySummaryArgs): F[Summary] =
    for {
      currentDate <- Async[F].memoize(Dates.currentDate(clock))
      from  <- args.from.fold(currentDate.map(_.withDayOfYear(1)))(_.pure[F])
      to    <- args.to.fold(currentDate)(_.pure[F])
      books <- transact(bookRepo.retrieveBooksInside(from, to))
      readBooks = books.filter(_.lastRead.nonEmpty)
      ratingAvg = mean(books.flatMap(_.rating.toList))
      montage <- montageService.montage(
        if (args.includeAdded) books else readBooks,
        args.montageInput
      )
    } yield Summary(readBooks.length, books.length, ratingAvg, montage)

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
