package fin

import fin.Types._
import java.time.LocalDate

object BookConversions {

  def toUserBook(
      book: BookInput,
      dateAdded: Option[LocalDate] = None,
      rating: Option[Int] = None,
      startedReading: Option[LocalDate] = None,
      lastRead: Option[LocalDate] = None,
      review: Option[String] = None
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
      lastRead,
      review
    )

  def toBookInput(userBook: UserBook): BookInput =
    BookInput(
      userBook.title,
      userBook.authors,
      userBook.description,
      userBook.isbn,
      userBook.thumbnailUri
    )
}
