package fin

import cats.implicits._

import fin.Types._

object SortConversions {
  def fromString(sort: String): Either[InvalidSortStringError, Sort] =
    sort.toLowerCase match {
      case "dateadded"     => Sort.DateAdded.asRight
      case "title"         => Sort.Title.asRight
      case "author"        => Sort.Author.asRight
      case "rating"        => Sort.Rating.asRight
      case unmatchedString => InvalidSortStringError(unmatchedString).asLeft
    }
}
