package fin.persistence

import java.time.LocalDate

import cats.data.OptionT
import cats.implicits._
import cats.{Monad, MonadThrow}
import doobie._
import doobie.implicits._
import doobie.util.fragment.Fragment

import fin.SortConversions
import fin.Types._

object SqliteCollectionRepository extends CollectionRepository[ConnectionIO] {

  import BookFragments._

  override def addBookToCollection(
      collectionName: String,
      book: BookInput,
      date: LocalDate
  ): ConnectionIO[Unit] =
    for {
      exists <- BookFragments.checkIsbn(book.isbn).query[String].option
      _ <- Monad[ConnectionIO].whenA(exists.isEmpty) {
        BookFragments.insert(book, date).update.run
      }
      _ <- BookFragments.addToCollection(collectionName, book.isbn).update.run
    } yield ()

  override def updateCollection(
      currentName: String,
      newName: String,
      preferredSort: Sort
  ): ConnectionIO[Unit] =
    if (currentName == newName)
      CollectionFragments.updateSort(currentName, preferredSort).update.run.void
    else
      CollectionFragments
        .create(newName, preferredSort.`type`, preferredSort.sortAscending)
        .update
        .run *>
        CollectionFragments
          .updateCollectonBooks(currentName, newName)
          .update
          .run *>
        CollectionFragments.delete(currentName).update.run.void

  override def collection(
      name: String,
      bookLimit: Option[Int],
      bookOffset: Option[Int]
  ): ConnectionIO[Option[Collection]] = {
    val limFrag =
      bookLimit
        .zip(bookOffset)
        .map { case (l, o) =>
          CollectionFragments.limitOffset(l, o)
        }
        .getOrElse(Fragment.empty)
    val nameFrag = CollectionFragments.fromName(name)
    val collectionOptionT = for {
      collectionInfo <- OptionT(
        (CollectionFragments.collectionInfo ++ nameFrag)
          .query[CollectionInfo]
          .option
      )
      books <- OptionT.liftF(
        (CollectionFragments
          .retrieveCollections(
            CollectionFragments.bookInfoSelection,
            fr"INNER"
          ) ++ nameFrag ++ CollectionFragments.orderBooks ++ limFrag)
          .query[BookRow]
          .to[List]
      )
      sortType <- OptionT.fromOption[ConnectionIO](
        SortConversions.fromString(collectionInfo.preferredSort).toOption
      )
    } yield Collection(
      collectionInfo.name,
      books.map(_.toBook),
      Sort(sortType, collectionInfo.sortAscending),
      PageInfo(collectionInfo.totalBooks).some
    )
    collectionOptionT.value
  }

  override def collections: ConnectionIO[List[Collection]] = {
    CollectionFragments
      .retrieveCollections(CollectionFragments.allSelection, fr"LEFT")
      .query[CollectionBookRow]
      .to[List]
      .flatMap(rows => MonadThrow[ConnectionIO].fromEither(toCollections(rows)))
  }

  override def createCollection(
      name: String,
      preferredSort: Sort
  ): ConnectionIO[Unit] = {
    CollectionFragments
      .create(name, preferredSort.`type`, preferredSort.sortAscending)
      .update
      .run
      .void
  }

  override def deleteCollection(name: String): ConnectionIO[Unit] =
    CollectionFragments
      .delete(name)
      .update
      .run
      .void

  override def removeBookFromCollection(
      collectionName: String,
      isbn: String
  ): ConnectionIO[Unit] =
    CollectionFragments
      .deleteReference(collectionName, isbn)
      .update
      .run
      .void

  private def toCollections(
      rows: List[CollectionBookRow]
  ): Either[Throwable, List[Collection]] = {
    rows
      .groupMapReduce(c => (c.name, c.preferredSort, c.sortAscending))(
        _.toBook.toList
      )(_ ++ _)
      .toList
      .traverse { case ((name, preferredSort, sortAscending), books) =>
        SortConversions
          .fromString(preferredSort)
          .map(t => Collection(name, books, Sort(t, sortAscending), None))
      }
  }
}

object CollectionFragments {

  implicit val sortPut: Put[SortType] = Put[String].contramap(_.toString)

  def collectionInfo =
    fr"""
       |SELECT
       |  c.name,
       |  c.preferred_sort,
       |  c.sort_ascending,
       |  ifnull(cb.count, 0)
       |FROM collections c
       |LEFT JOIN (SELECT collection_name, COUNT(*) as count
       |           FROM collection_books
       |           GROUP BY collection_name) cb
       |  ON c.name = cb.collection_name""".stripMargin

  def retrieveCollections(selection: Fragment, join: Fragment) =
    fr"SELECT" ++ selection ++
      fr"FROM collections c" ++ join ++
      fr"""
     |JOIN collection_books cb ON c.name = cb.collection_name
     |LEFT JOIN books b ON cb.isbn = b.isbn
     |LEFT JOIN currently_reading_books cr ON b.isbn = cr.isbn
     |LEFT JOIN (${BookFragments.lastRead}) lr ON b.isbn = lr.isbn
     |LEFT JOIN rated_books r ON b.isbn = r.isbn""".stripMargin

  val bookInfoSelection =
    fr"""
       |b.title,
       |b.authors,
       |b.description,
       |b.isbn,
       |b.thumbnail_uri,
       |b.added,
       |b.review,
       |cr.started,
       |lr.finished,
       |r.rating""".stripMargin

  val allSelection =
    fr"""
       |c.name,
       |c.preferred_sort,
       |c.sort_ascending,""".stripMargin ++ bookInfoSelection

  def fromName(name: String): Fragment = fr"WHERE name = $name"

  def create(
      name: String,
      preferredSort: SortType,
      sortAscending: Boolean
  ): Fragment =
    fr"""
       |INSERT INTO collections (name, preferred_sort, sort_ascending)
       |VALUES ($name, $preferredSort, $sortAscending)""".stripMargin

  def delete(name: String): Fragment =
    fr"DELETE FROM collections WHERE name = $name"

  def deleteReferences(name: String): Fragment =
    fr"DELETE FROM collection_books WHERE collection_name = $name"

  def deleteReference(name: String, isbn: String): Fragment =
    fr"""
       |DELETE FROM collection_books
       |WHERE collection_name = $name
       |AND isbn = $isbn""".stripMargin

  def updateSort(
      name: String,
      sort: Sort
  ): Fragment =
    fr"""
       |UPDATE collections
       |SET preferred_sort = ${sort.`type`},
       | sort_ascending = ${sort.sortAscending}
       |WHERE name = $name""".stripMargin

  def updateCollectonBooks(
      currentName: String,
      newName: String
  ): Fragment =
    fr"""
       |UPDATE collection_books
       |SET collection_name = $newName
       |WHERE collection_name = $currentName""".stripMargin

  val orderBooksCase: Fragment =
    fr"""
       |CASE
       |  WHEN c.preferred_sort = "DateAdded" THEN ${dateSort("b.added")}
       |  WHEN c.preferred_sort = "LastRead" THEN ${dateSort("lr.finished")}
       |  WHEN c.preferred_sort = "Title" THEN b.title
       |  WHEN c.preferred_sort = "Author" THEN b.authors
       |  ELSE r.rating
       |END
       |""".stripMargin

  val orderBooks: Fragment =
    fr"""
       |ORDER BY 
       |  (CASE sort_ascending WHEN 1 THEN $orderBooksCase ELSE NULL END) ASC,
       |  (CASE sort_ascending WHEN 0 THEN $orderBooksCase ELSE NULL END) DESC
       |""".stripMargin

  def dateSort(date: String): Fragment = {
    val dateFr = Fragment.const(date)
    List(fr"substr(", fr",1,4)||substr(", fr",6,2)||substr(", fr",9,2)")
      .intercalate(dateFr)
  }

  def limitOffset(limit: Int, offset: Int) =
    fr"LIMIT $limit OFFSET $offset"
}

final case class CollectionInfo(
    name: String,
    preferredSort: String,
    sortAscending: Boolean,
    totalBooks: Int
)

final case class CollectionBookRow(
    name: String,
    preferredSort: String,
    sortAscending: Boolean,
    maybeTitle: Option[String],
    maybeAuthors: Option[String],
    maybeDescription: Option[String],
    maybeIsbn: Option[String],
    maybeThumbnailUri: Option[String],
    maybeAdded: Option[LocalDate],
    maybeReview: Option[String],
    maybeStarted: Option[LocalDate],
    maybeFinished: Option[LocalDate],
    maybeRating: Option[Int]
) {
  def toBook: Option[UserBook] = {
    for {
      isbn         <- maybeIsbn
      title        <- maybeTitle
      authors      <- maybeAuthors
      description  <- maybeDescription
      thumbnailUri <- maybeThumbnailUri
    } yield UserBook(
      title = title,
      authors = authors.split(",").toList,
      description = description,
      isbn = isbn,
      thumbnailUri = thumbnailUri,
      dateAdded = maybeAdded,
      rating = maybeRating,
      startedReading = maybeStarted,
      lastRead = maybeFinished,
      review = maybeReview
    )
  }
}
