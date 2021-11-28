package fin.service.search

import cats.MonadThrow
import cats.effect.Concurrent
import cats.implicits._
import io.circe.parser.decode
import org.http4s._
import org.http4s.client._
import org.http4s.implicits._
import org.typelevel.log4cats.Logger

import fin.Types._
import fin._

import GoogleBooksAPIDecoding._

/**
  * A BookInfoService implementation which uses the <a href='https://developers.google.com/books/docs/v1/using'>Google Books API</a>
  *
  * @param client http client
  */
class GoogleBookInfoService[F[_]: Concurrent: Logger] private (
    client: Client[F]
) extends BookInfoService[F] {

  import GoogleBookInfoService._

  def search(booksArgs: QueriesBooksArgs): F[List[UserBook]] =
    for {
      uri   <- MonadThrow[F].fromEither(uriFromBooksArgs(booksArgs))
      _     <- Logger[F].debug(uri.toString)
      books <- booksFromUri(uri, searchPartialFn)
    } yield books

  def fromIsbn(bookArgs: QueriesBookArgs): F[List[UserBook]] = {
    val uri = uriFromBookArgs(bookArgs)
    for {
      _     <- Logger[F].debug(uri.toString)
      books <- booksFromUri(uri, isbnPartialFn)
    } yield books
  }

  private def booksFromUri(
      uri: Uri,
      pf: PartialFunction[GoogleVolume, UserBook]
  ): F[List[UserBook]] = {
    val request = Request[F](uri = uri, headers = headers)
    for {
      json <- client.expect[String](request)
      // We would have to use implicitly[MonadThrow[F]] without
      // import cats.effect.syntax._
      googleResponse <- MonadThrow[F].fromEither(decode[GoogleResponse](json))
      _              <- Logger[F].debug("DECODED: " + googleResponse)
    } yield googleResponse.items
      .getOrElse(List.empty)
      .sorted(responseOrdering)
      .collect(pf)
  }
}

/**
  * Utilities for decoding responses from the google books API
  */
object GoogleBookInfoService {

  val headers = Headers(
    ("Accept-Encoding", "gzip"),
    ("User-Agent", "finito (gzip)")
  )

  val noDescriptionFillIn = "No Description!"

  val responseOrdering: Ordering[GoogleVolume] =
    Ordering.by(gVolume => gVolume.volumeInfo.description.isEmpty)

  val searchPartialFn: PartialFunction[GoogleVolume, UserBook] = {
    case GoogleVolume(
          GoogleBookItem(
            Some(title),
            Some(authors),
            maybeDescription,
            Some(GoogleImageLinks(_, largeThumbnail)),
            Some(industryIdentifier :: _)
          )
        ) =>
      UserBook(
        title,
        authors,
        maybeDescription.getOrElse(noDescriptionFillIn),
        industryIdentifier.getIsbn13,
        largeThumbnail,
        None,
        None,
        None,
        None
      )
  }

  private val emptyThumbnailUri =
    "https://user-images.githubusercontent.com/17688577/131221362-c9fdb33a-e833-4469-8705-2c99a2b00fe3.png"

  val isbnPartialFn: PartialFunction[GoogleVolume, UserBook] = {
    case GoogleVolume(bookItem) =>
      UserBook(
        bookItem.title.getOrElse("???"),
        bookItem.authors.getOrElse(List("???")),
        bookItem.description.getOrElse(noDescriptionFillIn),
        bookItem.industryIdentifiers
          .getOrElse(Nil)
          .headOption
          .fold("???")(_.getIsbn13),
        bookItem.imageLinks.fold(emptyThumbnailUri)(_.thumbnail),
        None,
        None,
        None,
        None
      )
  }

  private val baseUri = uri"https://www.googleapis.com/books/v1/volumes"

  def apply[F[_]: Concurrent: Logger](client: Client[F]) =
    new GoogleBookInfoService[F](client)

  def uriFromBooksArgs(booksArgs: QueriesBooksArgs): Either[Throwable, Uri] =
    Either.cond(
      booksArgs.authorKeywords.exists(_.nonEmpty) ||
        booksArgs.titleKeywords.exists(_.nonEmpty),
      baseUri +? (
        (
          "q",
          (booksArgs.titleKeywords.filterNot(_.isEmpty).map("intitle:" + _) ++
            booksArgs.authorKeywords.filterNot(_.isEmpty).map("inauthor:" + _))
            .mkString("+")
        )
      ) +? (("fields", GoogleBooksAPIDecoding.fieldsSelector))
        +?? (("maxResults", booksArgs.maxResults))
        +?? (("langRestrict", booksArgs.langRestrict)),
      NoKeywordsSpecifiedError
    )

  def uriFromBookArgs(bookArgs: QueriesBookArgs): Uri =
    baseUri +? (("q", "isbn:" + bookArgs.isbn))
}
