package fin

import fin.Types._
import java.time.LocalDate

object BookConversions {

  implicit class BookInputSyntax(book: BookInput) {

    def toUserBook(
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
  }

  implicit class UserBookSyntax(userBook: UserBook) {

    def toBookInput: BookInput =
      BookInput(
        userBook.title,
        userBook.authors,
        userBook.description,
        userBook.isbn,
        userBook.thumbnailUri
      )
  }
}
