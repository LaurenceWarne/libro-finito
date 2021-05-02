package fin.api

final case class Book(
    title: String,
    author: String,
    description: String,
    isbn: String,
    thumbnailUri: String
)

final case class BookArgs(
    titleKeywords: Option[String],
    authorKeywords: Option[String]
    //dropValuesWithNoThumbnail: Option[Boolean] = true.some
)

final case class Queries[F[_]](
    books: BookArgs => F[List[Book]]
)
