package fin.service.book

import cats.effect.Concurrent
import cats.implicits._
import org.http4s.client._
import org.http4s.implicits._
import io.circe.parser.decode
import io.circe._
import io.circe.generic.semiauto._

import fin.Types._
import org.http4s._
import cats.MonadThrow
import fin.service.search.BookInfoService

class WikidataSeriesInfoService[F[_]: Concurrent](
    client: Client[F],
    bookInfoService: BookInfoService[F]
) extends SeriesInfoService[F] {

  import WikidataDecoding._

  private val uri     = uri"https://query.wikidata.org/sparql"
  private val headers = Headers(("Accept", "application/json"))

  override def series(args: QueriesSeriesArgs): F[List[UserBook]] = {
    val BookInput(title, author :: _, _, _, _) = args.book
    val body                                   = sparqlQuery(author, title)
    val request =
      Request[F](uri = uri +? ("query", body), headers = headers)
    for {
      json    <- client.expect[String](request)
      results <- MonadThrow[F].fromEither(decode[WikidataSeriesResponse](json))
      _ = identity(bookInfoService)
      _ = println(
        results.results.bindings.map(e =>
          (e.seriesBookLabel.value, e.ordinal.value)
        )
      )
    } yield List.empty

  }

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
