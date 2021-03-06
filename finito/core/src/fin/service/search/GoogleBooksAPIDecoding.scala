package fin.service.search

import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._

object GoogleBooksAPIDecoding {

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

  val fieldsSelector =
    "items/volumeInfo(title,authors,description,imageLinks,industryIdentifiers)"
}

final case class GoogleResponse(items: Option[List[GoogleVolume]])

final case class GoogleVolume(volumeInfo: GoogleBookItem)

final case class GoogleBookItem(
    // These are optional... because the API sometimes decides not to return them...
    title: Option[String],
    authors: Option[List[String]],
    description: Option[String],
    imageLinks: Option[GoogleImageLinks],
    industryIdentifiers: Option[List[GoogleIsbnInfo]]
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
    if (identifier.length === 10) "978" + identifier else identifier
}
