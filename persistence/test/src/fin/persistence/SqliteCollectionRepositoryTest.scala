package fin.persistence

import java.io.File

import cats.effect.{Clock, IO, Resource}
import cats.implicits._
import doobie.util.transactor.Transactor
import weaver._

import fin.Implicits._
import fin.Types._

object SqliteCollectionRepositoryTest extends IOSuite {

  val filename              = "tmp.db"
  val (uri, user, password) = (show"jdbc:sqlite:$filename", "", "")
  val xa = Transactor.fromDriverManager[IO](
    "org.sqlite.JDBC",
    uri,
    user,
    password
  )

  val repo = SqliteCollectionRepository(xa, Clock[IO])
  // We can't use the in memory db since that is killed whenever no connections
  // exist
  val deleteDb: IO[Unit] = for {
    file <- IO(new File(filename))
    _    <- IO(file.delete())
  } yield ()

  override type Res = Unit
  override def sharedResource: Resource[IO, Unit] =
    Resource.make(
      FlywaySetup.init[IO](
        uri,
        user,
        password
      )
    )(_ => deleteDb)

  // See https://www.sqlite.org/faq.html#q5 of why generally it's a bad idea
  // to run sqlite writes in parallel
  override def maxParallelism = 1

  test("createCollection creates collection with correct attributes") { _ =>
    val name = "my collection"
    for {
      collection <- repo.createCollection(name)
    } yield expect(collection === Collection(name, List.empty[Book]))
  }

  test("createCollection fails when name already exists") {
    val name = "duplicated_name"
    for {
      _        <- repo.createCollection(name)
      response <- repo.createCollection(name).attempt
    } yield expect(response.isLeft)
  }

  test("collection retrieves created collection") {
    val name = "retrieve_collection"
    for {
      persistedCollection <- repo.createCollection(name)
      retrievedCollection <- repo.collection(name)
    } yield expect(retrievedCollection.exists(_ === persistedCollection))
  }

  test("collections retrieves created collections") {
    for {
      c1                   <- repo.createCollection("collection1")
      c2                   <- repo.createCollection("collection2")
      c3                   <- repo.createCollection("collection3")
      retrievedCollections <- repo.collections
    } yield expect(Set(c1, c2, c3).subsetOf(retrievedCollections.toSet))
  }

  test("changeCollectionName changes collection name") {
    val oldName = "old_name"
    val newName = "new_name"
    for {
      _          <- repo.createCollection(oldName)
      collection <- repo.changeCollectionName(oldName, newName)
    } yield expect(collection.name === newName)
  }

  test("changeCollectionName fails if name already exists") {
    val oldName = "old_name_"
    val newName = "new_name_"
    for {
      _        <- repo.createCollection(oldName)
      _        <- repo.createCollection(newName)
      response <- repo.changeCollectionName(oldName, newName).attempt
    } yield expect(response.isLeft)
  }

  test("changeCollectionName fails if no experiment exists with name") {
    val name = "inexistant name"
    for {
      response <- repo.changeCollectionName(name, "???").attempt
    } yield expect(response.isLeft)
  }

  test("AddToCollection adds book not already added") {
    val name = "collection with books"
    val book = Book("title", List("author"), "cool description", "???", "uri")
    for {
      _          <- repo.createCollection(name)
      collection <- repo.addBookToCollection(name, book)
    } yield expect(collection === Collection(name, List(book)))
  }

  test("AddToCollection adds in another collection") {
    val name1 = "collection with books 1"
    val name2 = "collection with books 2"
    val book  = Book("title", List("author"), "cool description", "isbn", "uri")
    for {
      _          <- repo.createCollection(name1)
      _          <- repo.createCollection(name2)
      _          <- repo.addBookToCollection(name1, book)
      collection <- repo.addBookToCollection(name2, book)
    } yield expect(collection === Collection(name2, List(book)))
  }

  test("AddToCollection fails if collection does not exist") {
    val name = "inexistant collection #2"
    val book = Book("title", List("author"), "cool description", "isbn", "uri")
    for {
      response <- repo.addBookToCollection(name, book).attempt
    } yield expect(response.isLeft)
  }

  test("deleteCollection successful with collection with no books") {
    val name = "collection to delete"
    for {
      _               <- repo.createCollection(name)
      _               <- repo.deleteCollection(name)
      maybeCollection <- repo.collection(name)
    } yield expect(maybeCollection.isEmpty)
  }

  test("deleteCollection successful with collection with one book") {
    val name = "collection to delete with books"
    val book =
      Book("title", List("author"), "cool description", "isbn-d", "uri")
    for {
      _               <- repo.createCollection(name)
      _               <- repo.addBookToCollection(name, book)
      _               <- repo.deleteCollection(name)
      maybeCollection <- repo.collection(name)
    } yield expect(maybeCollection.isEmpty)
  }
}
