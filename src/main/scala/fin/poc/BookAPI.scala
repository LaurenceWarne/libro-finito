package fin.poc

import cats.effect.Sync
import cats.effect.syntax._
import org.http4s.client.blaze._
import org.http4s.client._
import cats.effect.ConcurrentEffect
import org.http4s.blaze.http.HttpClient
import cats.implicits._
import io.circe._
import io.circe.parser.decode
import io.circe.generic.semiauto._
import cats.MonadError

trait BookAPI[F[_]] {
  def search(bookArgs: BookArgs): F[List[Book]]
}

final case class BookArgs(
    titleKeywords: Option[String],
    authorKeywords: Option[String]
)

final case class GoogleBookAPI[F[_]: ConcurrentEffect](client: Client[F])
    extends BookAPI[F] {

  import GoogleBookAPI._

  private val emptyThumbnailUri =
    "https://user-images.githubusercontent.com/101482/29592647-40da86ca-875a-11e7-8bc3-941700b0a323.png"

  def search(bookArgs: BookArgs): F[List[Book]] = {
    val queryStr = (bookArgs.titleKeywords.map("intitle:" + _).toList ++
      bookArgs.authorKeywords.map("inauthor:" + _).toList)
      .mkString("+")
    for {
      json <- client.expect[String](
        s"https://www.googleapis.com/books/v1/volumes?q=$queryStr&printType=books"
      )
      _ = println(decode[GoogleResponse](json))
      // We would have to use implicitly[ConcurrentEffect[F]] without
      // import cats.effect.syntax._
      googleResponse <-
        ConcurrentEffect[F].fromEither(decode[GoogleResponse](json))
      _ = println(googleResponse)
    } yield googleResponse.items.map(bookItem =>
      Book(
        bookItem.volumeInfo.title,
        bookItem.volumeInfo.authors.head,
        bookItem.volumeInfo.description.getOrElse("No Description!"),
        bookItem.volumeInfo.industryIdentifiers.head.getIsbn13,
        bookItem.volumeInfo.imageLinks.fold(emptyThumbnailUri)(_.thumbnail)
      )
    )
  }
}

object GoogleBookAPI {

  implicit val googleIsbnInfoDecoder: Decoder[GoogleIsbnInfo] =
    deriveDecoder[GoogleIsbnInfo]

  implicit val googleImageLinksDecoder: Decoder[GoogleImageLinks] =
    deriveDecoder[GoogleImageLinks]

  implicit val googleBookItemDecoder: Decoder[GoogleBookItem] =
    deriveDecoder[GoogleBookItem]

  implicit val googleVolumeDecoder: Decoder[GoogleVolume] =
    deriveDecoder[GoogleVolume]

  implicit val googleResponseDecoder: Decoder[GoogleResponse] =
    deriveDecoder[GoogleResponse]
}

final case class GoogleResponse(items: List[GoogleVolume])

final case class GoogleVolume(volumeInfo: GoogleBookItem)

final case class GoogleBookItem(
    title: String,
    authors: List[String],
    description: Option[String],
    imageLinks: Option[GoogleImageLinks],
    industryIdentifiers: List[GoogleIsbnInfo]
)

final case class GoogleImageLinks(
    smallThumbnail: String,
    thumbnail: String
)

final case class GoogleIsbnInfo(
    `type`: String,
    identifier: String
) {
  def getIsbn13: String =
    if (identifier.length == 10) "978" + identifier else identifier
}
