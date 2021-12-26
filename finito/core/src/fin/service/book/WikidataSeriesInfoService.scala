package fin.service.book

import cats.effect.Concurrent
import cats.implicits._
import cats.{MonadThrow, Parallel}
import io.circe._
import io.circe.generic.semiauto._
import io.circe.parser.decode
import org.http4s._
import org.http4s.client._
import org.http4s.implicits._

import fin.Types._
import fin.service.search.BookInfoService

class WikidataSeriesInfoService[F[_]: Concurrent: Parallel] private (
    client: Client[F],
    bookInfoService: BookInfoService[F]
) extends SeriesInfoService[F] {

  import WikidataDecoding._

  private val uri     = uri"https://query.wikidata.org/sparql"
  private val headers = Headers(("Accept", "application/json"))

  override def series(args: QueriesSeriesArgs): F[List[UserBook]] = {
    val BookInput(title, authors, _, _, _) = args.book
    val author                             = authors.headOption.getOrElse("???")
    val body                               = sparqlQuery(author, title)
    val request =
      Request[F](uri = uri +? (("query", body)), headers = headers)
    for {
      json     <- client.expect[String](request)
      response <- MonadThrow[F].fromEither(decode[WikidataSeriesResponse](json))
      titlesAndStrOrdinals =
        response.results.bindings
          .map(e => (e.seriesBookLabel.value, e.ordinal.value))
          .distinct
      titlesAndOrdinals <- titlesAndStrOrdinals.traverse {
        case (title, ordinalStr) =>
          MonadThrow[F]
            .fromOption(
              ordinalStr.toIntOption,
              new Exception(
                show"Expected int for ordinal of $title, but was $ordinalStr"
              )
            )
            .tupleLeft(title)
      }
      booksAndOrdinals <- titlesAndOrdinals.parTraverse {
        case (title, ordinal) =>
          topSearchResult(author, title).tupleRight(ordinal)
      }
    } yield booksAndOrdinals.sortBy(_._2).map(_._1)
  }

  private def topSearchResult(
      author: String,
      title: String
  ): F[UserBook] =
    for {
      books <-
        bookInfoService
          .search(
            QueriesBooksArgs(title.some, author.some, None, None)
          )
      book <- MonadThrow[F].fromOption(
        books.headOption,
        new Exception(show"No book found for $title and $author")
      )
    } yield book

  private def sparqlQuery(author: String, title: String): String =
    s"""
      |SELECT ?book ?seriesBookLabel ?ordinal WHERE {
      |  ?book wdt:P31 wd:Q7725634.
      |  ?book rdfs:label "$title"@en.
      |  ?book wdt:P50 ?author.
      |  ?author rdfs:label "$author"@en.
      |  ?book wdt:P179 ?series.
      |  ?series wdt:P527 ?seriesBook.
      |  ?seriesBook p:P179 ?membership.
      |  ?membership pq:P1545 ?ordinal.
      |  SERVICE wikibase:label { bd:serviceParam wikibase:language "en".}
      |} limit 100""".stripMargin
}

object WikidataSeriesInfoService {
  def apply[F[_]: Concurrent: Parallel](
      client: Client[F],
      bookInfoService: BookInfoService[F]
  ) = new WikidataSeriesInfoService[F](client, bookInfoService)
}

object WikidataDecoding {
  implicit val wikidataBookOrdinalDecoder: Decoder[WikidataBookOrdinal] =
    deriveDecoder[WikidataBookOrdinal]

  implicit val wikidataBookLabelDecoder: Decoder[WikidataBookLabel] =
    deriveDecoder[WikidataBookLabel]

  implicit val wikidatSeriesEntryDecoder: Decoder[WikidataSeriesEntry] =
    deriveDecoder[WikidataSeriesEntry]

  implicit val wikidataBindingsDecoder: Decoder[WikidataBindings] =
    deriveDecoder[WikidataBindings]

  implicit val wikidataSeriesResponseDecoder: Decoder[WikidataSeriesResponse] =
    deriveDecoder[WikidataSeriesResponse]
}

case class WikidataSeriesResponse(results: WikidataBindings)

case class WikidataBindings(bindings: List[WikidataSeriesEntry])

case class WikidataSeriesEntry(
    seriesBookLabel: WikidataBookLabel,
    ordinal: WikidataBookOrdinal
)

case class WikidataBookLabel(value: String)

case class WikidataBookOrdinal(value: String)
