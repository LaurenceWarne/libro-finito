package fin

import cats.implicits._

import fin.Types._

object SortConversions {
  def fromString(sortType: String): Either[InvalidSortStringError, SortType] =
    sortType.toLowerCase match {
      case "dateadded"     => SortType.DateAdded.asRight
      case "title"         => SortType.Title.asRight
      case "author"        => SortType.Author.asRight
      case "rating"        => SortType.Rating.asRight
      case "lastread"      => SortType.LastRead.asRight
      case unmatchedString => InvalidSortStringError(unmatchedString).asLeft
    }
}
