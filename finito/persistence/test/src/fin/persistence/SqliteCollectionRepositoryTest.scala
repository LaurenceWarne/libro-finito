package fin.persistence

import java.time.LocalDate

import cats.implicits._

import fin.BookConversions._
import fin.Types._
import fin.implicits._

object SqliteCollectionRepositoryTest extends SqliteSuite {

  val book =
    BookInput(
      "title",
      List("author"),
      "cool description",
      "???",
      "uri"
    )
  val repo = SqliteCollectionRepository
  val date = LocalDate.of(2021, 5, 22)

  testDoobie("collection retrieves created collection") {
    val name = "retrieve_collection"
    for {
      _                   <- repo.createCollection(name, Sort.DateAdded)
      retrievedCollection <- repo.collection(name)
    } yield expect(
      retrievedCollection.exists(
        _ === Collection(name, List.empty, Sort.DateAdded)
      )
    )
  }

  testDoobie("createCollection fails when name already exists") {
    val name = "duplicated_name"
    for {
      _        <- repo.createCollection(name, Sort.DateAdded)
      response <- repo.createCollection(name, Sort.DateAdded).attempt
    } yield expect(response.isLeft)
  }

  testDoobie("collections retrieves created collections") {
    val (name1, name2, name3) = ("collection1", "collection2", "collection3")
    for {
      _                    <- repo.createCollection(name1, Sort.DateAdded)
      _                    <- repo.createCollection(name2, Sort.DateAdded)
      _                    <- repo.createCollection(name3, Sort.DateAdded)
      retrievedCollections <- repo.collections
    } yield expect(
      Set(name1, name2, name3).subsetOf(retrievedCollections.map(_.name).toSet)
    )
  }

  testDoobie("updateCollection changes collection name and sort") {
    val oldName = "old_name"
    val newName = "new_name"
    val oldSort = Sort.DateAdded
    val newSort = Sort.Title
    for {
      _                   <- repo.createCollection(oldName, oldSort)
      _                   <- repo.updateCollection(oldName, newName, newSort)
      retrievedCollection <- repo.collection(newName)
    } yield expect(
      retrievedCollection.exists(c =>
        c.name === newName && c.preferredSort === newSort
      )
    )
  }

  testDoobie(
    "updateCollection changes collection name and sort for collection with books"
  ) {
    val oldName = "old_name with books"
    val newName = "new_name with books"
    val oldSort = Sort.DateAdded
    val newSort = Sort.Title
    for {
      _                   <- repo.createCollection(oldName, oldSort)
      _                   <- repo.addBookToCollection(oldName, book, date)
      _                   <- repo.updateCollection(oldName, newName, newSort)
      retrievedCollection <- repo.collection(newName)
    } yield expect(
      retrievedCollection.exists(c =>
        c === Collection(newName, List(toUserBook(book)), newSort)
      )
    )
  }

  testDoobie("updateCollection errors if name already exists") {
    val oldName = "old_name_"
    val newName = "new_name_"
    val sort    = Sort.DateAdded
    for {
      _        <- repo.createCollection(oldName, sort)
      _        <- repo.createCollection(newName, sort)
      response <- repo.updateCollection(oldName, newName, sort).attempt
    } yield expect(response.isLeft)
  }

  testDoobie(
    "updateCollection does not error if no experiment exists with name"
  ) {
    val name = "inexistant name"
    for {
      response <- repo.updateCollection(name, "???", Sort.DateAdded).attempt
    } yield expect(response.isRight)
  }

  testDoobie("AddToCollection adds book not already added") {
    val name = "collection with books"
    val sort = Sort.DateAdded
    for {
      _                   <- repo.createCollection(name, sort)
      _                   <- repo.addBookToCollection(name, book, date)
      retrievedCollection <- repo.collection(name)
    } yield expect(
      retrievedCollection.exists(
        _ === Collection(name, List(toUserBook(book)), sort)
      )
    )
  }

  testDoobie("AddToCollection adds in another collection") {
    val name1 = "collection with books 1"
    val name2 = "collection with books 2"
    val sort  = Sort.DateAdded
    for {
      _                   <- repo.createCollection(name1, sort)
      _                   <- repo.createCollection(name2, sort)
      _                   <- repo.addBookToCollection(name1, book, date)
      _                   <- repo.addBookToCollection(name2, book, date)
      retrievedCollection <- repo.collection(name2)
    } yield expect(
      retrievedCollection.exists(
        _ === Collection(name2, List(toUserBook(book)), sort)
      )
    )
  }

  testDoobie("AddToCollection errors if collection does not exist") {
    val name = "inexistant collection #2"
    for {
      response <- repo.addBookToCollection(name, book, date).attempt
    } yield expect(response.isLeft)
  }

  testDoobie("deleteCollection successful with collection with no books") {
    val name = "collection to delete"
    for {
      _               <- repo.createCollection(name, Sort.DateAdded)
      _               <- repo.deleteCollection(name)
      maybeCollection <- repo.collection(name)
    } yield expect(maybeCollection.isEmpty)
  }

  testDoobie("deleteCollection successful with collection with one book") {
    val name  = "collection to delete with books"
    val book2 = book.copy(isbn = "isbn-d")
    for {
      _               <- repo.createCollection(name, Sort.DateAdded)
      _               <- repo.addBookToCollection(name, book2, date)
      _               <- repo.deleteCollection(name)
      maybeCollection <- repo.collection(name)
    } yield expect(maybeCollection.isEmpty)
  }

  testDoobie("deleteCollection does not error when collection does not exist") {
    for {
      response <- repo.deleteCollection("inexistant collection").attempt
    } yield expect(response.isRight)
  }

  testDoobie(
    "removeBookFromCollection successful with collection with one book"
  ) {
    val name  = "collection with book to delete"
    val book2 = book.copy(isbn = "isbn-d")
    for {
      _               <- repo.createCollection(name, Sort.DateAdded)
      _               <- repo.addBookToCollection(name, book2, date)
      _               <- repo.removeBookFromCollection(name, book2.isbn)
      maybeCollection <- repo.collection(name)
    } yield expect(maybeCollection.exists(_.books.isEmpty))
  }

  testDoobie(
    "removeBookFromCollection successful when collection does not exist"
  ) {
    val isbn = "isbn-d"
    for {
      response <-
        repo.removeBookFromCollection("inexistant collection", isbn).attempt
    } yield expect(response.isRight)
  }

  testDoobie(
    "removeBookFromCollection successful when connection does not exist"
  ) {
    val name = "collection with no book"
    val isbn = "isbn-d"
    for {
      _        <- repo.createCollection(name, Sort.DateAdded)
      response <- repo.removeBookFromCollection(name, isbn).attempt
    } yield expect(response.isRight)
  }
}
