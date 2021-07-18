package fin.persistence

import java.sql.Date
import java.time.LocalDate

import scala.concurrent.duration.DAYS

import cats.Monad
import cats.effect.{Clock, Sync}
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.util.fragment.Fragment
import doobie.util.transactor.Transactor

import fin.Constants
import fin.SortConversions
import fin.Types._

class SqliteCollectionRepository[F[_]: Sync] private (
    xa: Transactor[F],
    clock: Clock[F]
) extends CollectionRepository[F] {

  override def addBookToCollection(
      collectionName: String,
      book: Book
  ): F[Unit] = {
    val transaction: Date => ConnectionIO[Unit] = date =>
      for {
        exists <- BookFragments.retrieveByIsbn(book.isbn).query[String].option
        _ <- Monad[ConnectionIO].whenA(exists.isEmpty) {
          BookFragments.insert(book, date).update.run
        }
        _ <- BookFragments.addToCollection(collectionName, book.isbn).update.run
      } yield ()
    for {
      date <-
        clock
          .monotonic(DAYS)
          .map(t => Date.valueOf(LocalDate.ofEpochDay(t)))
      _ <- transaction(date).transact(xa)
    } yield ()
  }

  override def updateCollection(
      currentName: String,
      newName: String,
      preferredSort: Sort
  ): F[Unit] = {
    Fragments
      .update(currentName, newName, preferredSort)
      .update
      .run
      .transact(xa)
      .void
  }

  override def collection(name: String): F[Option[Collection]] =
    Fragments
      .fromName(name)
      .query[CollectionBookRow]
      .to[List]
      .transact(xa)
      .flatMap(rows => Sync[F].fromEither(toCollections(rows)))
      .map(_.headOption)

  override def collections: F[List[Collection]] =
    Fragments.retrieveCollections
      .query[CollectionBookRow]
      .to[List]
      .transact(xa)
      .flatMap(rows => Sync[F].fromEither(toCollections(rows)))

  override def createCollection(
      name: String,
      preferredSort: Sort
  ): F[Unit] = {
    Fragments
      .create(name, preferredSort)
      .update
      .run
      .transact(xa)
      .void
  }

  override def deleteCollection(name: String): F[Unit] =
    Fragments
      .delete(name)
      .update
      .run
      .transact(xa)
      .void

  override def removeBookFromCollection(
      collectionName: String,
      isbn: String
  ): F[Unit] =
    Fragments
      .deleteReference(collectionName, isbn)
      .update
      .run
      .transact(xa)
      .void

  private def toCollections(
      rows: List[CollectionBookRow]
  ): Either[Throwable, List[Collection]] = {
    rows
      .groupMapReduce(c => (c.name, c.preferredSort))(_.asBook.toList)(_ ++ _)
      .toList
      .traverse {
        case ((name, preferredSort), books) =>
          SortConversions
            .fromString(preferredSort)
            .map(Collection(name, books, _))
      }
  }
}

object SqliteCollectionRepository {

  def apply[F[_]: Sync](xa: Transactor[F], clock: Clock[F]) =
    new SqliteCollectionRepository[F](xa, clock)
}

object Fragments {

  implicit val sortPut: Put[Sort] = Put[String].contramap(_.toString)

  val retrieveCollections =
    fr"""
       |SELECT 
       |  c.name,
       |  c.preferred_sort,
       |  b.isbn,
       |  b.title, 
       |  b.authors,
       |  b.description,
       |  b.thumbnail_uri,
       |  b.added
       |FROM collections AS c
       |LEFT JOIN collection_books AS cb ON c.name = cb.collection_name
       |LEFT JOIN books AS b ON cb.isbn = b.isbn""".stripMargin

  def fromName(name: String): Fragment =
    retrieveCollections ++ fr"WHERE name = $name"

  def create(name: String, preferredSort: Sort): Fragment =
    fr"""
       |INSERT INTO collections (name, preferred_sort)
       |VALUES ($name, $preferredSort)""".stripMargin

  def delete(name: String): Fragment =
    fr"DELETE FROM collections WHERE name = $name"

  def deleteReferences(name: String): Fragment =
    fr"DELETE FROM collection_books WHERE collection_name = $name"

  def deleteReference(name: String, isbn: String): Fragment =
    fr"""
       |DELETE FROM collection_books
       |WHERE collection_name = $name
       |AND isbn = $isbn""".stripMargin

  def update(
      currentName: String,
      newName: String,
      preferredSort: Sort
  ): Fragment =
    fr"""
       |UPDATE collections 
       |SET name = $newName, preferred_sort = $preferredSort
       |WHERE name = $currentName""".stripMargin
}

case class CollectionBookRow(
    name: String,
    preferredSort: String,
    maybeIsbn: Option[String],
    maybeTitle: Option[String],
    maybeAuthors: Option[String],
    maybeDescription: Option[String],
    maybeThumbnailUri: Option[String],
    maybeAdded: Option[Date]
) {
  def asBook: Option[Book] = {
    for {
      isbn         <- maybeIsbn
      title        <- maybeTitle
      authors      <- maybeAuthors
      description  <- maybeDescription
      thumbnailUri <- maybeThumbnailUri
    } yield Book(
      title = title,
      authors = authors.split(",").toList,
      description = description,
      isbn = isbn,
      thumbnailUri = thumbnailUri,
      Constants.emptyUserData
    )
  }
}
