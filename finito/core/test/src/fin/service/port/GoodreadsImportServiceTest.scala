package fin.service.port

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.arrow.FunctionK
import cats.effect._
import cats.effect.std.Random
import cats.implicits._
import org.typelevel.cats.time._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver._

import fin.Types._
import fin.fixtures
import fin.persistence.BookRepository
import fin.service.book._
import fin.service.collection._
import fin.service.port._

object GoodreadsImportServiceTest extends IOSuite {

  val defaultCollectionBook = fixtures.bookInput
  val collection1           = "cool-stuff-collection"
  val collection2           = "the-best-collection"
  val (title1, title2)      = ("Gardens of the Moon", "The Caves of Steel")
  val rating                = 5
  val dateRead              = LocalDate.of(2023, 1, 13)
  val dateReadStr = dateRead.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))

  def csv(random: Random[IO]): IO[(String, String, String)] =
    for {
      isbn1 <- random.nextString(10)
      isbn2 <- random.nextString(10)
    } yield (
      isbn1,
      isbn2,
      show"""Book Id,Title,Author,Author l-f,Additional Authors,ISBN,ISBN13,My Rating,Average Rating,Publisher,Binding,Number of Pages,Year Published,Original Publication Year,Date Read,Date Added,Bookshelves,Bookshelves with positions,Exclusive Shelf,My Review,Spoiler,Private Notes,Read Count,Owned Copies
13450209,"$title1",Steven Erikson,"Erikson, Steven",,$isbn1,9781409083108,$rating,3.92,Transworld Publishers,ebook,768,2009,1999,$dateReadStr,2023/01/01,"$collection1, favorites, $collection2","$collection1 (#2), favorites (#1), $collection2 (#1)",read,,,,1,0
11097712,"$title2",Isaac Asimov,"Asimov, Isaac",,$isbn2,=9780307792419,0,4.19,Spectra,Kindle Edition,271,2011,1953,,2021/09/04,,,currently-reading,,,,1,0"""
    )

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override type Res =
    (
        GoodreadsImportService[IO],
        BookRepository[IO],
        CollectionService[IO],
        Random[IO]
    )

  override def sharedResource: Resource[IO, Res] =
    for {
      colRef <- Resource.eval(Ref.of[IO, List[Collection]](List.empty))
      collectionService = CollectionServiceImpl[IO, IO](
        new InMemoryCollectionRepository(colRef),
        Clock[IO],
        FunctionK.id[IO]
      )
      bookRef <- Resource.eval(Ref.of[IO, List[UserBook]](List.empty))
      bookRepo = new InMemoryBookRepository[IO](bookRef)
      bookService = BookManagementServiceImpl[IO, IO](
        bookRepo,
        Clock[IO],
        FunctionK.id[IO]
      )
      books = List(
        fixtures.userBook.copy(title = title1),
        fixtures.userBook.copy(title = title2)
      )
      bookInfoService = new BookInfoServiceUsingTitles(books)

      importService = GoodreadsImportService[IO](
        bookInfoService,
        collectionService,
        bookService,
        bookService
      )
      random <- Resource.eval(Random.scalaUtilRandom[IO])
    } yield (importService, bookRepo, collectionService, random)

  test("importResource creates books") {
    case (importService, bookRepo, _, rnd) =>
      for {
        (isbn1, isbn2, csv) <- csv(rnd)
        importResult        <- importService.importResource(csv, None)
        book1               <- bookRepo.retrieveBook(isbn1)
        book2               <- bookRepo.retrieveBook(isbn2)
      } yield expect(book1.nonEmpty) &&
        expect(book2.nonEmpty) &&
        expect(importResult.successful.length == 2) &&
        expect(importResult.partiallySuccessful.length == 0) &&
        expect(importResult.unsuccessful.length == 0)
  }

  test("importResource adds books to correct collections") {
    case (importService, _, collectionService, rnd) =>
      for {
        (_, _, csv) <- csv(rnd)
        _           <- importService.importResource(csv, None)
        collection <- collectionService.collection(
          QueryCollectionArgs(collection1, None)
        )
        books      = collection.books
        bookTitles = books.map(_.title)
      } yield expect(bookTitles.contains_(title1)) &&
        expect(!bookTitles.contains_(title2))
  }

  test("importResource marks books as started") {
    case (importService, bookRepo, _, rnd) =>
      for {
        (isbn1, isbn2, csv) <- csv(rnd)
        _                   <- importService.importResource(csv, None)
        book1               <- bookRepo.retrieveBook(isbn1)
        book2               <- bookRepo.retrieveBook(isbn2)
      } yield expect(book1.exists(_.startedReading.isEmpty)) &&
        expect(book2.exists(_.startedReading.nonEmpty))
  }

  test("importResource marks books as finished") {
    case (importService, bookRepo, _, rnd) =>
      for {
        (isbn1, isbn2, csv) <- csv(rnd)
        _                   <- importService.importResource(csv, None)
        book1               <- bookRepo.retrieveBook(isbn1)
        book2               <- bookRepo.retrieveBook(isbn2)
      } yield expect(book1.flatMap(_.lastRead).contains_(dateRead)) &&
        expect(book2.exists(_.lastRead.isEmpty))
  }

  test("importResource rates books") { case (importService, bookRepo, _, rnd) =>
    for {
      (isbn1, isbn2, csv) <- csv(rnd)
      _                   <- importService.importResource(csv, None)
      book1               <- bookRepo.retrieveBook(isbn1)
      book2               <- bookRepo.retrieveBook(isbn2)
    } yield expect(book1.flatMap(_.rating).contains_(rating)) &&
      expect(book2.exists(_.rating.isEmpty))
  }
}
