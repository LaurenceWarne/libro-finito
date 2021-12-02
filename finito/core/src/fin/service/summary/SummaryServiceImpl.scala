package fin.service.summary

import cats.~>
import cats.effect._
import cats.implicits._

import java.time.LocalDate

import fin.Types._
import fin.persistence.{BookRepository, Dates}

class SummaryServiceImpl[F[_]: Async, G[_]](
    bookRepo: BookRepository[G],
    montageService: MontageService[F],
    transact: G ~> F,
    clock: Clock[F]
) extends SummaryService[F] {

  override def summary(
      maybeFrom: Option[LocalDate],
      maybeTo: Option[LocalDate]
  ): F[Summary] =
    for {
      currentDate <- Async[F].memoize(Dates.currentDate(clock))
      from        <- maybeFrom.fold(currentDate.map(_.withDayOfYear(1)))(_.pure[F])
      to          <- maybeTo.fold(currentDate)(_.pure[F])
      books       <- transact(bookRepo.retrieveBooksInside(from, to))
      read      = books.filter(_.lastRead.nonEmpty).length
      ratingAvg = mean(books.flatMap(_.rating.toList))
      montage <- montageService.montage(books)
      _ = println(montage.length())
    } yield Summary(books.length, read, ratingAvg, "???")

  private def mean(ls: List[Int]): Float =
    if (ls.isEmpty) 0f else (ls.sum / ls.size).toFloat
}