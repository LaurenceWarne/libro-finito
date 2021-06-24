package fin

import Types._

import cats.effect.IO

object Types {
  case class QueriesBooksArgs(
      titleKeywords: Option[String],
      authorKeywords: Option[String]
  )
  case class Book(
      title: String,
      author: String,
      description: String,
      isbn: String,
      thumbnailUri: String
  )

}

object Operations {

  case class Queries(
      books: QueriesBooksArgs => IO[List[Book]]
  )

}

