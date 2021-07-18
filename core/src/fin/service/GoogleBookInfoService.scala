package fin.service

import cats.MonadError
import cats.effect.ConcurrentEffect
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.generic.semiauto._
import io.circe.parser.decode
import org.http4s.Uri
import org.http4s.client._
import org.http4s.implicits._

import fin.Constants
import fin.Types._

/**
  * A BookInfoService implementation which uses the <a href='https://developers.google.com/books/docs/v1/using'>Google Books API</a>
  *
  * @param client http client
  */
class GoogleBookInfoService[F[_]: ConcurrentEffect: Logger] private (
    client: Client[F]
) extends BookInfoService[F] {

  import GoogleBookInfoService._

  def search(booksArgs: QueriesBooksArgs): F[List[Book]] =
    for {
      uri   <- MonadError[F, Throwable].fromEither(uriFromBooksArgs(booksArgs))
      _     <- Logger[F].info(uri.toString)
      books <- booksFromUri(uri, searchPartialFn)
    } yield books

  def fromIsbn(bookArgs: QueriesBookArgs): F[Book] = {
    val uri = uriFromBookArgs(bookArgs)
    for {
      _     <- Logger[F].info(uri.toString)
      books <- booksFromUri(uri, isbnPartialFn)
      book <- MonadError[F, Throwable].fromOption(
        books.headOption,
        new Exception(show"No books found for isbn: ${bookArgs.isbn}")
      )
    } yield book
  }

  private def booksFromUri(
      uri: Uri,
      pf: PartialFunction[GoogleVolume, Book]
  ): F[List[Book]] = {
    for {
      json <- client.expect[String](uri)
      _    <- Logger[F].info(decode[GoogleResponse](json).toString)
      // We would have to use implicitly[MonadError[F, Throwable]] without
      // import cats.effect.syntax._
      googleResponse <-
        MonadError[F, Throwable]
          .fromEither(decode[GoogleResponse](json))
      _ <- Logger[F].debug("DECODED: " + googleResponse)
    } yield googleResponse.items.collect(pf)
  }
}

case object NoKeywordsSpecified extends Throwable {
  override def getMessage: String =
    "At least one of 'author keywords' and 'title keywords' must be specified."
}

/**
  * Utilities for decoding responses from the google books API
  */
object GoogleBookInfoService {

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

  val searchPartialFn: PartialFunction[GoogleVolume, Book] = {
    case GoogleVolume(
          GoogleBookItem(
            title,
            Some(authors),
            maybeDescription,
            Some(GoogleImageLinks(_, largeThumbnail)),
            Some(industryIdentifier :: _)
          )
        ) =>
      Book(
        title,
        authors,
        maybeDescription.getOrElse("No Description!"),
        industryIdentifier.getIsbn13,
        largeThumbnail,
        Constants.emptyUserData
      )
  }

  private val emptyThumbnailUri =
    "https://user-images.githubusercontent.com/101482/29592647-40da86ca-875a-11e7-8bc3-941700b0a323.png"

  val isbnPartialFn: PartialFunction[GoogleVolume, Book] = {
    case GoogleVolume(bookItem) =>
      Book(
        bookItem.title,
        bookItem.authors.getOrElse(List("???")),
        bookItem.description.getOrElse("No Description!"),
        bookItem.industryIdentifiers
          .getOrElse(Nil)
          .headOption
          .fold("???")(_.getIsbn13),
        bookItem.imageLinks.fold(emptyThumbnailUri)(_.thumbnail),
        Constants.emptyUserData
      )
  }

  private val baseUri = uri"https://www.googleapis.com/books/v1/volumes"

  def apply[F[_]: ConcurrentEffect: Logger](client: Client[F]) =
    new GoogleBookInfoService[F](client)

  def uriFromBooksArgs(booksArgs: QueriesBooksArgs): Either[Throwable, Uri] =
    Either.cond(
      booksArgs.authorKeywords
        .filterNot(_.isEmpty)
        .nonEmpty || booksArgs.titleKeywords.filterNot(_.isEmpty).nonEmpty,
      baseUri +? (
        "q",
        (booksArgs.titleKeywords.filterNot(_.isEmpty).map("intitle:" + _) ++
          booksArgs.authorKeywords.map("inauthor:" + _))
          .mkString("+")
      ) +?? ("maxResults", booksArgs.maxResults),
      NoKeywordsSpecified
    )

  def uriFromBookArgs(bookArgs: QueriesBookArgs): Uri =
    baseUri +? ("q", "isbn:" + bookArgs.isbn)
}

final case class GoogleResponse(items: List[GoogleVolume])

final case class GoogleVolume(volumeInfo: GoogleBookItem)

final case class GoogleBookItem(
    title: String,
    // These are optional... because the API sometimes decides not to return them...
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
    if (identifier.length == 10) "978" + identifier else identifier
}
