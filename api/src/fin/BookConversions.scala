package fin

import fin.Types._
import java.time.LocalDate

object BookConversions {

  def toUserBook(
      book: BookInput,
      rating: Option[Int] = None,
      startedReading: Option[LocalDate] = None,
      lastRead: Option[LocalDate] = None
  ): UserBook =
    UserBook(
      book.title,
      book.authors,
      book.description,
      book.isbn,
      book.thumbnailUri,
      rating,
      startedReading,
      lastRead
    )
}
