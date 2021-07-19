package fin

import fin.Types._
import java.time.Instant

object BookConversions {

  def toUserBook(
      book: BookInput,
      rating: Option[Int] = None,
      startedReading: Option[Instant] = None,
      lastRead: Option[Instant] = None
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
