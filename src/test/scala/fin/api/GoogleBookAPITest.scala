package fin.api

import cats.Id
import cats.effect._
import cats.implicits._
import weaver.SimpleIOSuite
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.Response
import fs2.Stream

object GoogleBookAPITest extends SimpleIOSuite {

  def mockedClient(response: String): Client[IO] =
    Client.apply[IO](_ =>
      Resource.pure[IO, Response[IO]](
        Response[IO](body = Stream.emits(response.getBytes("UTF-8")))
      )
    )

  test("search parses title, author and description from json") {
    val title = "The Casual Vacancy"
    val author = "J K Rowling"
    val description = "Not Harry Potter"
    val client: Client[IO] =
      mockedClient(Mocks.jsonResponse(title, author, description))
    val bookAPI: BookAPI[IO] = GoogleBookAPI(client)
    for {
      result <- bookAPI.search(BookArgs("non-empty".some, None))
      book :: Nil = result
    } yield expect(book.title === title) and
      expect(book.author === author) and
      expect(book.description === description)
  }

  pureTest("uriFromBookArgs returns correct uri") {
    val bookArgs = BookArgs("title".some, "some author".some)
    val Right(uri) = GoogleBookAPI.uriFromArgs(bookArgs)
    expect(
      uri === uri"https://www.googleapis.com/books/v1/volumes?q=intitle%3Atitle%2Binauthor%3Asome%20author"
    )
  }

  pureTest("uriFromBookArgs errors with empty strings") {
    val bookArgs = BookArgs("".some, "".some)
    expect(GoogleBookAPI.uriFromArgs(bookArgs).isLeft)
  }

  pureTest("uriFromBookArgs errors with empty optionals") {
    val bookArgs = BookArgs(None, None)
    expect(GoogleBookAPI.uriFromArgs(bookArgs).isLeft)
  }

  pureTest("uriFromBookArgs returns uri with no title with None for title") {
    val bookArgs = BookArgs(None, "author".some)
    val Right(uri) = GoogleBookAPI.uriFromArgs(bookArgs)
    expect(
      uri === uri"https://www.googleapis.com/books/v1/volumes?q=inauthor%3Aauthor"
    )
  }

  pureTest("uriFromBookArgs returns uri with no author with None for author") {
    val bookArgs = BookArgs("title".some, None)
    val Right(uri) = GoogleBookAPI.uriFromArgs(bookArgs)
    expect(
      uri === uri"https://www.googleapis.com/books/v1/volumes?q=intitle%3Atitle"
    )
  }
}

object Mocks {
  def jsonResponse(title: String, author: String, description: String): String =
    s"""{
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
}
