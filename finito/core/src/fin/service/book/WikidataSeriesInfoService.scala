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
import org.typelevel.log4cats.Logger

import fin.Types._
import fin.service.search.BookInfoService

class WikidataSeriesInfoService[F[_]: Concurrent: Parallel: Logger] private (
    client: Client[F],
    bookInfoService: BookInfoService[F]
) extends SeriesInfoService[F] {

  import WikidataDecoding._

  private val uri     = uri"https://query.wikidata.org/sparql"
  private val headers = Headers(("Accept", "application/json"))

  override def series(args: QuerySeriesArgs): F[List[UserBook]] = {
    val BookInput(title, authors, _, _, _) = args.book
    val author                             = authors.headOption.getOrElse("???")
    // Here we and try work around Wikidata using author names like 'Iain Banks' rather than 'Iain M. Banks'
    val authorFallback = author.split(" ").toList match {
      case first :: (_ +: List(last)) => s"$first $last"
      case _                          => author
    }
    val body = sparqlQuery(List(author, authorFallback).distinct, title)
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
      booksAndOrdinals <- titlesAndOrdinals.parFlatTraverse {
        case (title, ordinal) =>
          topSearchResult(author, title).map(_.tupleRight(ordinal).toList)
      }
    } yield booksAndOrdinals.sortBy(_._2).map(_._1)
  }

  private def topSearchResult(
      author: String,
      title: String
  ): F[Option[UserBook]] =
    for {
      books <-
        bookInfoService
          .search(
            QueryBooksArgs(title.some, author.some, None, None)
          )
      _ <- MonadThrow[F].whenA(books.isEmpty) {
        Logger[F].warn(
          show"No book information found for $title and $author, not showing in series"
        )
      }
    } yield books.headOption

  private def sparqlQuery(authors: List[String], title: String): String = {
    val authorFilter = authors
      .map(a => s"""?authorLabel = "$a"@en""")
      .mkString("FILTER(", " || ", ")")

    s"""
      |SELECT ?book ?seriesBookLabel ?ordinal WHERE {
      |  ?book wdt:P31 wd:Q7725634.
      |  ?book rdfs:label "$title"@en.
      |  ?book wdt:P50 ?author.
      |  ?author rdfs:label ?authorLabel.
      |  $authorFilter
      |  ?book wdt:P179 ?series.
      |  ?series wdt:P527 ?seriesBook.
      |  ?seriesBook p:P179 ?membership.
      |  ?membership pq:P1545 ?ordinal.
      |  SERVICE wikibase:label { bd:serviceParam wikibase:language "en".}
      |} limit 100""".stripMargin

  }
}

object WikidataSeriesInfoService {
  def apply[F[_]: Concurrent: Parallel: Logger](
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

final case class WikidataSeriesResponse(results: WikidataBindings)

final case class WikidataBindings(bindings: List[WikidataSeriesEntry])

final case class WikidataSeriesEntry(
    seriesBookLabel: WikidataBookLabel,
    ordinal: WikidataBookOrdinal
)

final case class WikidataBookLabel(value: String)

final case class WikidataBookOrdinal(value: String)
