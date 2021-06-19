package fin.api

import cats.effect.Sync
import cats.effect.syntax._
import org.http4s.client.blaze._
import org.http4s.client._
import org.http4s.implicits._
import org.http4s.Uri
import org.http4s.QueryParam._
import cats.effect.ConcurrentEffect
import org.http4s.blaze.http.HttpClient
import cats.implicits._
import io.circe._
import io.circe.parser.decode
import io.circe.generic.semiauto._
import cats.MonadError

/**
  * A BookAPI implementation which uses the <a href='https://developers.google.com/books/docs/v1/using'>Google Books API</a>
  *
  * @param client http client
  */
final case class GoogleBookAPI[F[_]: ConcurrentEffect](client: Client[F])
    extends BookAPI[F] {

  import GoogleBookAPI._

  private val emptyThumbnailUri =
    "https://user-images.githubusercontent.com/101482/29592647-40da86ca-875a-11e7-8bc3-941700b0a323.png"

  def search(bookArgs: BookArgs): F[List[Book]] = {
    for {
      uri <- MonadError[F, Throwable].fromEither(uriFromArgs(bookArgs))
      _ = println(uri)
      json <- client.expect[String](uri)
      // We would have to use implicitly[MonadError[F, Throwable]] without
      // import cats.effect.syntax._
      googleResponse <-
        MonadError[F, Throwable].fromEither(decode[GoogleResponse](json))
      _ = println("DECODED: " + decode[GoogleResponse](json))
    } yield googleResponse.items.collect {
      case GoogleVolume(
            GoogleBookItem(
              title,
              author :: otherAuthors,
              maybeDescription,
              Some(GoogleImageLinks(_, largeThumbnail)),
              industryIdentifier :: otherIdentifiers
            )
          ) =>
        Book(
          title,
          author,
          maybeDescription.getOrElse("No Description!"),
          industryIdentifier.getIsbn13,
          largeThumbnail
        )
    }
  }
}

/**
  * Utilities for decoding responses from the google books API
  */
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

  def uriFromArgs(bookArgs: BookArgs): Either[Exception, Uri] =
    Either.cond(
      bookArgs.authorKeywords
        .filterNot(_.isEmpty)
        .nonEmpty || bookArgs.titleKeywords.filterNot(_.isEmpty).nonEmpty,
      uri"https://www.googleapis.com/books/v1/volumes" +? (
        "q",
        (bookArgs.titleKeywords.filterNot(_.isEmpty).map("intitle:" + _) ++
          bookArgs.authorKeywords.map("inauthor:" + _))
          .mkString("+")
      ),
      new Exception(
        "At least one of author keywords and title keywords must be specified"
      )
    )
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
