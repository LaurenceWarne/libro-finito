package fin

import java.time.{LocalDate, ZoneId}

import scala.concurrent.duration._

import cats._
import cats.effect._
import cats.implicits._
import org.http4s.Response
import org.http4s.client.Client

import fin.Types._
import fin._

package object fixtures {

  val date = LocalDate.parse("2021-11-30")

  val clock = TestClock[IO](
    date
      .atStartOfDay(ZoneId.systemDefault())
      .toEpochSecond * 1000L
  )

  val author = "William Gibson"
  val (title1, title2, title3) =
    ("Neuromancer", "Count Zero", "Mona Lisa Overdrive")

  val bookInput = BookInput(
    title1,
    List(author),
    "description",
    "isbn",
    "???"
  )

  val emptyBook =
    UserBook("", List.empty, "", "", "", None, None, None, None, None)

  def HTTPClient(response: String): Client[IO] =
    Client.apply[IO](_ =>
      Resource.pure[IO, Response[IO]](
        Response[IO](body = fs2.Stream.emits(response.getBytes("UTF-8")))
      )
    )

  object BooksResponses {

    def response(
        title: String,
        author: String,
        description: String
    ): String =
      show"""{
  "kind": "books#volumes",
  "totalItems": 210,
  "items": [
    {
      "kind": "books#volume",
      "id": "FjMbGietIZ4C",
      "etag": "W5uM+Ex4qLI",
      "selfLink": "https://www.googleapis.com/books/v1/volumes/FjMbGietIZ4C",
      "volumeInfo": {
        "title": "$title",
        "authors": [
          "$author"
        ],
        "publisher": "Hachette UK",
        "publishedDate": "2012-09-27",
        "description": "$description",
        "industryIdentifiers": [
          {
            "type": "ISBN_13",
            "identifier": "9781405519229"
          },
          {
            "type": "ISBN_10",
            "identifier": "1405519223"
          }
        ],
        "readingModes": {
          "text": true,
          "image": false
        },
        "pageCount": 480,
        "printType": "BOOK",
        "categories": [
          "Fiction"
        ],
        "averageRating": 3,
        "ratingsCount": 622,
        "maturityRating": "NOT_MATURE",
        "allowAnonLogging": true,
        "contentVersion": "1.14.15.0.preview.2",
        "panelizationSummary": {
          "containsEpubBubbles": false,
          "containsImageBubbles": false
        },
        "imageLinks": {
          "smallThumbnail": "http://books.google.com/books/content?id=FjMbGietIZ4C&printsec=frontcover&img=1&zoom=5&edge=curl&source=gbs_api",
          "thumbnail": "http://books.google.com/books/content?id=FjMbGietIZ4C&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api"
        },
        "language": "en",
        "previewLink": "http://books.google.co.uk/books?id=FjMbGietIZ4C&printsec=frontcover&dq=casual+inauthor:rowling&hl=&as_pt=BOOKS&cd=1&source=gbs_api",
        "infoLink": "https://play.google.com/store/books/details?id=FjMbGietIZ4C&source=gbs_api",
        "canonicalVolumeLink": "https://play.google.com/store/books/details?id=FjMbGietIZ4C"
      },
      "saleInfo": {
        "country": "GB",
        "saleability": "FOR_SALE",
        "isEbook": true,
        "listPrice": {
          "amount": 5.99,
          "currencyCode": "GBP"
        },
        "retailPrice": {
          "amount": 5.99,
          "currencyCode": "GBP"
        },
        "buyLink": "https://play.google.com/store/books/details?id=FjMbGietIZ4C&rdid=book-FjMbGietIZ4C&rdot=1&source=gbs_api",
        "offers": [
          {
            "finskyOfferType": 1,
            "listPrice": {
              "amountInMicros": 5990000,
              "currencyCode": "GBP"
            },
            "retailPrice": {
              "amountInMicros": 5990000,
              "currencyCode": "GBP"
            },
            "giftable": true
          }
        ]
      },
      "accessInfo": {
        "country": "GB",
        "viewability": "PARTIAL",
        "embeddable": true,
        "publicDomain": false,
        "textToSpeechPermission": "ALLOWED_FOR_ACCESSIBILITY",
        "epub": {
          "isAvailable": true,
          "acsTokenLink": "http://books.google.co.uk/books/download/The_Casual_Vacancy-sample-epub.acsm?id=FjMbGietIZ4C&format=epub&output=acs4_fulfillment_token&dl_type=sample&source=gbs_api"
        },
        "pdf": {
          "isAvailable": false
        },
        "webReaderLink": "http://play.google.com/books/reader?id=FjMbGietIZ4C&hl=&as_pt=BOOKS&printsec=frontcover&source=gbs_api",
        "accessViewStatus": "SAMPLE",
        "quoteSharingAllowed": false
      },
      "searchInfo": {
        "textSnippet": "Who will triumph in an election fraught with passion, duplicity and unexpected revelations? A big novel about a small town, THE CASUAL VACANCY is J.K. Rowling&#39;s first novel for adults. It is the work of a storyteller like no other."
      }
    }
  ]
}
"""

    def emptyResponse = """{
  "kind": "books#volumes",
  "totalItems": 0
}
"""

    def isbnResponse(isbn: String) =
      show"""{
  "kind": "books#volumes",
  "totalItems": 1,
  "items": [
    {
      "kind": "books#volume",
      "id": "RuAcAAAACAAJ",
      "etag": "lMAxu0DYzVs",
      "selfLink": "https://www.googleapis.com/books/v1/volumes/RuAcAAAACAAJ",
      "volumeInfo": {
        "title": "The Book of the New Sun",
        "subtitle": "The Shadow of the Torturer ; The Claw of the Conciliator ; The Sword of the Lictor ; The Citadel of the Autarch",
        "authors": [
          "Gene Wolfe"
        ],
        "publishedDate": "1998",
        "description": "Shadow of the torturer.; Claw of the conciliator.; Sword of the lictor.; Citadel of the autarch.",
        "industryIdentifiers": [
          {
            "type": "ISBN_10",
            "identifier": "$isbn"
          },
          {
            "type": "ISBN_13",
            "identifier": "9781568658070"
          }
        ],
        "readingModes": {
          "text": false,
          "image": false
        },
        "pageCount": 950,
        "printType": "BOOK",
        "categories": [
          "Fantasy fiction, American"
        ],
        "averageRating": 4,
        "ratingsCount": 5,
        "maturityRating": "NOT_MATURE",
        "allowAnonLogging": false,
        "contentVersion": "preview-1.0.0",
        "language": "un",
        "previewLink": "http://books.google.co.uk/books?id=RuAcAAAACAAJ&dq=isbn:1568658079&hl=&cd=1&source=gbs_api",
        "infoLink": "http://books.google.co.uk/books?id=RuAcAAAACAAJ&dq=isbn:1568658079&hl=&source=gbs_api",
        "canonicalVolumeLink": "https://books.google.com/books/about/The_Book_of_the_New_Sun.html?hl=&id=RuAcAAAACAAJ"
      },
      "saleInfo": {
        "country": "GB",
        "saleability": "NOT_FOR_SALE",
        "isEbook": false
      },
      "accessInfo": {
        "country": "GB",
        "viewability": "NO_PAGES",
        "embeddable": false,
        "publicDomain": false,
        "textToSpeechPermission": "ALLOWED",
        "epub": {
          "isAvailable": false
        },
        "pdf": {
          "isAvailable": false
        },
        "webReaderLink": "http://play.google.com/books/reader?id=RuAcAAAACAAJ&hl=&printsec=frontcover&source=gbs_api",
        "accessViewStatus": "NONE",
        "quoteSharingAllowed": false
      },
      "searchInfo": {
        "textSnippet": "Shadow of the torturer.; Claw of the conciliator.; Sword of the lictor.; Citadel of the autarch."
      }
    }
  ]
}"""
  }

  object SeriesResponses {
    def trilogy(title1: String, title2: String, title3: String): String =
      show"""{
  "head" : {
    "vars" : [ "book", "seriesBookLabel", "ordinal" ]
  },
  "results" : {
    "bindings" : [ {
      "book" : {
        "type" : "uri",
        "value" : "http://www.wikidata.org/entity/Q662029"
      },
      "seriesBookLabel" : {
        "xml:lang" : "en",
        "type" : "literal",
        "value" : "$title1"
      },
      "ordinal" : {
        "type" : "literal",
        "value" : "1"
      }
    }, {
      "book" : {
        "type" : "uri",
        "value" : "http://www.wikidata.org/entity/Q662029"
      },
      "seriesBookLabel" : {
        "xml:lang" : "en",
        "type" : "literal",
        "value" : "$title3"
      },
      "ordinal" : {
        "type" : "literal",
        "value" : "3"
      }
    }, {
      "book" : {
        "type" : "uri",
        "value" : "http://www.wikidata.org/entity/Q662029"
      },
      "seriesBookLabel" : {
        "xml:lang" : "en",
        "type" : "literal",
        "value" : "$title2"
      },
      "ordinal" : {
        "type" : "literal",
        "value" : "2"
      }
    } ]
  }
}"""

    val badOrdinal = """{
  "head" : {
    "vars" : [ "book", "seriesBookLabel", "ordinal" ]
  },
  "results" : {
    "bindings" : [ {
      "book" : {
        "type" : "uri",
        "value" : "http://www.wikidata.org/entity/Q662029"
      },
      "seriesBookLabel" : {
        "xml:lang" : "en",
        "type" : "literal",
        "value" : "some-title"
      },
      "ordinal" : {
        "type" : "literal",
        "value" : "not-an-integer"
      }
    } ]
  }
}"""
  }
}

/**
  * A Test clock that always returns a constant time.
  *
  * @param epoch the constant time as a unix epoch
  */
final case class TestClock[F[_]: Applicative](epoch: Long) extends Clock[F] {

  override def applicative = implicitly[Applicative[F]]

  override def realTime: F[FiniteDuration] =
    FiniteDuration(epoch, MILLISECONDS).pure[F]

  override def monotonic: F[FiniteDuration] =
    FiniteDuration(epoch, MILLISECONDS).pure[F]
}
