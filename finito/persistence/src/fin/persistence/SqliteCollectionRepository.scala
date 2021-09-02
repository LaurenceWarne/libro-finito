package fin.persistence

import java.time.LocalDate

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
      exists <- BookFragments.retrieveByIsbn(book.isbn).query[String].option
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
      Fragments.updateSort(currentName, preferredSort).update.run.void
    else
      Fragments
        .create(newName, preferredSort.`type`, preferredSort.sortAscending)
        .update
        .run *>
        Fragments.updateCollectonBooks(currentName, newName).update.run *>
        Fragments.delete(currentName).update.run.void

  override def collection(name: String): ConnectionIO[Option[Collection]] =
    Fragments
      .fromName(name)
      .query[CollectionBookRow]
      .to[List]
      .flatMap(rows => MonadThrow[ConnectionIO].fromEither(toCollections(rows)))
      .map(_.headOption)

  override def collections: ConnectionIO[List[Collection]] =
    Fragments.retrieveCollections
      .query[CollectionBookRow]
      .to[List]
      .flatMap(rows => MonadThrow[ConnectionIO].fromEither(toCollections(rows)))

  override def createCollection(
      name: String,
      preferredSort: Sort
  ): ConnectionIO[Unit] = {
    Fragments
      .create(name, preferredSort.`type`, preferredSort.sortAscending)
      .update
      .run
      .void
  }

  override def deleteCollection(name: String): ConnectionIO[Unit] =
    Fragments
      .delete(name)
      .update
      .run
      .void

  override def removeBookFromCollection(
      collectionName: String,
      isbn: String
  ): ConnectionIO[Unit] =
    Fragments
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
      .traverse {
        case ((name, preferredSort, sortAscending), books) =>
          SortConversions
            .fromString(preferredSort)
            .map(t => Collection(name, books, Sort(t, sortAscending)))
      }
  }
}

object Fragments {

  implicit val sortPut: Put[SortType] = Put[String].contramap(_.toString)

  val retrieveCollections =
    fr"""
       |SELECT 
       |  c.name,
       |  c.preferred_sort,
       |  c.sort_ascending,
       |  b.isbn,
       |  b.title, 
       |  b.authors,
       |  b.description,
       |  b.thumbnail_uri,
       |  b.added,
       |  cr.started,
       |  lr.finished,
       |  r.rating
       |FROM collections c
       |LEFT JOIN collection_books cb ON c.name = cb.collection_name
       |LEFT JOIN books b ON cb.isbn = b.isbn
       |LEFT JOIN currently_reading_books cr ON b.isbn = cr.isbn
       |LEFT JOIN (${BookFragments.lastRead}) lr ON b.isbn = lr.isbn
       |LEFT JOIN rated_books r ON b.isbn = r.isbn""".stripMargin

  def fromName(name: String): Fragment =
    retrieveCollections ++ fr"WHERE name = $name"

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
}

final case class CollectionBookRow(
    name: String,
    preferredSort: String,
    sortAscending: Boolean,
    maybeIsbn: Option[String],
    maybeTitle: Option[String],
    maybeAuthors: Option[String],
    maybeDescription: Option[String],
    maybeThumbnailUri: Option[String],
    maybeAdded: Option[LocalDate],
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
      maybeAdded,
      maybeRating,
      maybeStarted,
      maybeFinished
    )
  }
}
