package fin.persistence

import doobie.ConnectionIO

import fin.Types._
import java.time.LocalDate

trait CollectionRepository {
  def collections: ConnectionIO[List[Collection]]
  def createCollection(name: String, preferredSort: Sort): ConnectionIO[Unit]
  def collection(name: String): ConnectionIO[Option[Collection]]
  def deleteCollection(name: String): ConnectionIO[Unit]
  def updateCollection(
      currentName: String,
      newName: String,
      preferredSort: Sort
  ): ConnectionIO[Unit]
  def addBookToCollection(
      collectionName: String,
      book: BookInput,
      date: LocalDate
  ): ConnectionIO[Unit]
  def removeBookFromCollection(
      collectionName: String,
      isbn: String
  ): ConnectionIO[Unit]
}
