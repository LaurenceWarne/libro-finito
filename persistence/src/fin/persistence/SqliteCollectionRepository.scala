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

import fin.Types._

class SqliteCollectionRepository[F[_]: Sync] private (
    xa: Transactor[F],
    clock: Clock[F]
) extends CollectionRepository[F] {

  override def addBookToCollection(
      collectionName: String,
      book: Book
  ): F[Collection] = {
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
      _               <- transaction(date).transact(xa)
      maybeCollection <- collection(collectionName)
      collection <- Sync[F].fromOption(
        maybeCollection,
        new Exception(show"Collection '$collectionName' was deleted!")
      )
    } yield collection
  }

  override def changeCollectionName(
      currentName: String,
      newName: String
  ): F[Collection] = {
    val transaction: ConnectionIO[String] = for {
      _          <- Fragments.update(currentName, newName).update.run
      collection <- Fragments.fromName(newName).query[String].unique
    } yield collection
    transaction
      .transact(xa)
      .map(s => Collection(s, Nil))
  }

  override def collection(name: String): F[Option[Collection]] =
    Fragments
      .fromName(name)
      .query[CollectionBookRow]
      .to[List]
      .transact(xa)
      .map(toCollections(_).headOption)

  override def collections: F[List[Collection]] =
    Fragments.retrieveCollections
      .query[CollectionBookRow]
      .to[List]
      .transact(xa)
      .map(toCollections(_))

  override def createCollection(name: String): F[Collection] = {
    Fragments
      .create(name)
      .update
      .run
      .transact(xa)
      .map(_ => Collection(name, List.empty[Book]))
  }

  override def deleteCollection(name: String): F[Unit] =
    (Fragments.deleteReferences(name).update.run *> Fragments
      .delete(name)
      .update
      .run).transact(xa).void

  override def removeBookFromCollection(
      collectionName: String,
      book: Book
  ): F[Unit] =
    Fragments
      .deleteReference(collectionName, book.isbn)
      .update
      .run
      .transact(xa)
      .void

  private def toCollections(rows: List[CollectionBookRow]): List[Collection] = {
    rows
      .groupMapReduce(_.name)(_.asBook.toList)(_ ++ _)
      .map {
        case (name, books) =>
          Collection(name, books)
      }
      .toList
  }
}

object SqliteCollectionRepository {

  def apply[F[_]: Sync](xa: Transactor[F], clock: Clock[F]) =
    new SqliteCollectionRepository[F](xa, clock)
}

object Fragments {

  val retrieveCollections =
    fr"""
       |SELECT 
       |  c.name,
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

  def create(name: String): Fragment =
    fr"INSERT INTO collections (name) VALUES($name)"

  def delete(name: String): Fragment =
    fr"DELETE FROM collections WHERE name = $name"

  def deleteReferences(name: String): Fragment =
    fr"DELETE FROM collection_books WHERE collection_name = $name"

  def deleteReference(name: String, isbn: String): Fragment =
    fr"""
       |DELETE FROM collection_books
       |WHERE collection_name = $name
       |AND isbn = $isbn""".stripMargin

  def update(currentName: String, newName: String): Fragment =
    fr"UPDATE collections SET name = $newName WHERE name = $currentName"
}

object BookFragments {

  def retrieveByIsbn(isbn: String): Fragment =
    fr"select isbn from books WHERE isbn=$isbn"

  def insert(book: Book, date: Date): Fragment =
    fr"""
       |INSERT INTO books VALUES (
       |  ${book.isbn},
       |  ${book.title},
       |  ${book.authors.mkString(",")},
       |  ${book.description},
       |  ${book.thumbnailUri},
       |  $date
       |)""".stripMargin

  def addToCollection(collectionName: String, isbn: String): Fragment =
    fr"INSERT INTO collection_books VALUES ($collectionName, $isbn)"
}

case class CollectionBookRow(
    name: String,
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
      thumbnailUri = thumbnailUri
    )
  }
}
