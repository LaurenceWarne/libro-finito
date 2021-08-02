package fin

import fin.Types._
import java.time.LocalDate

object BookConversions {

  def toUserBook(
      book: BookInput,
      dateAdded: Option[LocalDate] = None,
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
      dateAdded,
      rating,
      startedReading,
      lastRead
    )
}
