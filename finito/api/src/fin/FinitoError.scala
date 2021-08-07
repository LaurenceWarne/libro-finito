package fin

import cats.implicits._

import implicits._
import Types._

trait FinitoError extends Throwable {
  def errorCode: String
}

case object NoKeywordsSpecifiedError extends FinitoError {
  override def getMessage =
    "At least one of 'author keywords' and 'title keywords' must be specified."
  override def errorCode = "NO_KEYWORDS_SPECIFIED"
}

final case class NoBooksFoundForIsbnError(isbn: String) extends FinitoError {
  override def getMessage = show"No books found for isbn: '$isbn'"
  override def errorCode  = "NO_BOOKS_FOR_ISBN"
}

case object CannotChangeNameOfSpecialCollectionError extends FinitoError {
  override def getMessage = "Cannot update the name of a special collection"
  override def errorCode  = "CANNOT_CHANGE_NAME_OF_SPECIAL_COLLECTION"
}

case object CannotDeleteSpecialCollectionError extends FinitoError {
  override def getMessage =
    """
     |Cannot delete a special collection!  In order to delete a special
     |collection, first remove it's special collection definition from your
     |config file, and then delete it.""".stripMargin.replace("\n", " ")
  override def errorCode = "CANNOT_DELETE_SPECIAL_COLLECTION"
}

final case class CollectionDoesNotExistError(collection: String)
    extends FinitoError {
  override def getMessage = show"Collection '$collection' does not exist!"
  override def errorCode  = "NOT_ENOUGH_ARGS_FOR_UPDATE"
}

final case class CollectionAlreadyExistsError(collection: String)
    extends FinitoError {
  override def getMessage = show"Collection '$collection' already exists!"
  override def errorCode  = "NOT_ENOUGH_ARGS_FOR_UPDATE"
}

case object NotEnoughArgumentsForUpdateError extends FinitoError {
  override def getMessage =
    """
     |At least one of 'newName', 'preferredSortType' or 'sortAscending'
     |must be specified""".stripMargin.replace("\n", " ")
  override def errorCode = "NOT_ENOUGH_ARGS_FOR_UPDATE"
}

case object DefaultCollectionNotSupportedError extends FinitoError {
  override def getMessage = "The default collection is not known!"
  override def errorCode  = "DEFAULT_COLLECTION_NOT_SUPPORTED"
}

final case class BookAlreadyBeingReadError(book: BookInput)
    extends FinitoError {
  override def getMessage =
    show"The book '${book.title}' is already being read!"
  override def errorCode = "BOOK_ALREADY_BEING_READ"
}

final case class BookAlreadyExistsError(book: BookInput) extends FinitoError {
  override def getMessage =
    show"A book with isbn ${book.isbn} already exists: $book!"
  override def errorCode = "BOOK_ALREADY_EXISTS"
}

final case class InvalidSortStringError(string: String) extends FinitoError {
  override def getMessage = show"$string is not a valid sort type!"
  override def errorCode  = "INVALID_SORT_STRING"
}
