package fin.service.book

import cats.MonadError
import cats.effect.ConcurrentEffect
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.circe.parser.decode
import org.http4s.Uri
import org.http4s.client._
import org.http4s.implicits._

import fin.Types._
import fin._

import GoogleBooksAPIDecoding._

/**
  * A BookInfoService implementation which uses the <a href='https://developers.google.com/books/docs/v1/using'>Google Books API</a>
  *
  * @param client http client
  */
class GoogleBookInfoService[F[_]: ConcurrentEffect: Logger] private (
    client: Client[F]
) extends BookInfoService[F] {

  import GoogleBookInfoService._

  def search(booksArgs: QueriesBooksArgs): F[List[UserBook]] =
    for {
      uri   <- MonadError[F, Throwable].fromEither(uriFromBooksArgs(booksArgs))
      _     <- Logger[F].info(uri.toString)
      books <- booksFromUri(uri, searchPartialFn)
    } yield books

  def fromIsbn(bookArgs: QueriesBookArgs): F[List[UserBook]] = {
    val uri = uriFromBookArgs(bookArgs)
    for {
      _     <- Logger[F].info(uri.toString)
      books <- booksFromUri(uri, isbnPartialFn)
    } yield books
  }

  private def booksFromUri(
      uri: Uri,
      pf: PartialFunction[GoogleVolume, UserBook]
  ): F[List[UserBook]] = {
    for {
      json <- client.expect[String](uri)
      _    <- Logger[F].info(decode[GoogleResponse](json).toString)
      // We would have to use implicitly[MonadError[F, Throwable]] without
      // import cats.effect.syntax._
      googleResponse <-
        MonadError[F, Throwable]
          .fromEither(decode[GoogleResponse](json))
      _ <- Logger[F].debug("DECODED: " + googleResponse)
    } yield googleResponse.items.getOrElse(List.empty).collect(pf)
  }
}

/**
  * Utilities for decoding responses from the google books API
  */
object GoogleBookInfoService {

  val searchPartialFn: PartialFunction[GoogleVolume, UserBook] = {
    case GoogleVolume(
          GoogleBookItem(
            title,
            Some(authors),
            maybeDescription,
            Some(GoogleImageLinks(_, largeThumbnail)),
            Some(industryIdentifier :: _)
          )
        ) =>
      UserBook(
        title,
        authors,
        maybeDescription.getOrElse("No Description!"),
        industryIdentifier.getIsbn13,
        largeThumbnail,
        None,
        None,
        None,
        None
      )
  }

  private val emptyThumbnailUri =
    "https://user-images.githubusercontent.com/101482/29592647-40da86ca-875a-11e7-8bc3-941700b0a323.png"

  val isbnPartialFn: PartialFunction[GoogleVolume, UserBook] = {
    case GoogleVolume(bookItem) =>
      UserBook(
        bookItem.title,
        bookItem.authors.getOrElse(List("???")),
        bookItem.description.getOrElse("No Description!"),
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
      ) +?? ("maxResults", booksArgs.maxResults)
        +?? ("langRestrict", booksArgs.langRestrict),
      NoKeywordsSpecifiedError
    )

  def uriFromBookArgs(bookArgs: QueriesBookArgs): Uri =
    baseUri +? ("q", "isbn:" + bookArgs.isbn)
}
