package fin.service

import cats.effect.concurrent.Ref
import cats.effect.{IO, Resource}
import cats.implicits._
import weaver._

import fin.Types._
import fin.implicits._

object CollectionServiceImplTest extends IOSuite {

  override type Res = CollectionService[IO]
  override def sharedResource: Resource[IO, CollectionService[IO]] =
    Resource.eval(Ref.of[IO, List[Collection]](List.empty).map { ref =>
      CollectionServiceImpl(new InMemoryCollectionRepository(ref))
    })

  test("createCollection creates collection") { collectionService =>
    val name = "name"
    for {
      collection <- collectionService.createCollection(
        MutationsCreateCollectionArgs(name, None)
      )
    } yield expect(collection.name === name)
  }

  test("createCollection errors if collection already exists") {
    collectionService =>
      val name = "already existing"
      for {
        _ <- collectionService.createCollection(
          MutationsCreateCollectionArgs(name, None)
        )
        response <-
          collectionService
            .createCollection(
              MutationsCreateCollectionArgs(name, None)
            )
            .attempt
      } yield expect(response.isLeft)
  }

  test("collection returns created collection") { collectionService =>
    val name = "name to retrieve"
    for {
      _ <- collectionService.createCollection(
        MutationsCreateCollectionArgs(name, None)
      )
      retrievedCollection <-
        collectionService.collection(QueriesCollectionArgs(name))
    } yield expect(retrievedCollection.name === name)
  }

  test("collection errors on inexistant collection") { collectionService =>
    for {
      response <-
        collectionService
          .collection(QueriesCollectionArgs("not a collection!"))
          .attempt
    } yield expect(response.isLeft)
  }

  test("collections returns created collections") { collectionService =>
    val (name1, name2) = ("name1", "name2")
    for {
      _ <- collectionService.createCollection(
        MutationsCreateCollectionArgs(name1, None)
      )
      _ <- collectionService.createCollection(
        MutationsCreateCollectionArgs(name2, None)
      )
      retrievedCollection <- collectionService.collections
    } yield expect(
      Set(name1, name2).subsetOf(retrievedCollection.map(_.name).toSet)
    )
  }

  test("deleteCollection deletes collection") { collectionService =>
    val name = "collection to delete"
    for {
      _ <- collectionService.createCollection(
        MutationsCreateCollectionArgs(name, None)
      )
      _ <-
        collectionService
          .deleteCollection(MutationsDeleteCollectionArgs(name))
      collections <- collectionService.collections
    } yield expect(!collections.map(_.name).contains(name))
  }

  test("deleteCollection does not error on inexistant collection") {
    collectionService =>
      val name = "inexistant collection"
      for {
        response <-
          collectionService
            .deleteCollection(MutationsDeleteCollectionArgs(name))
            .attempt
      } yield expect(response.isRight)
  }

  test("updateCollectionName udpates collection name") { collectionService =>
    val (oldName, newName) = ("old name", "new name")
    for {
      _ <-
        collectionService
          .createCollection(MutationsCreateCollectionArgs(oldName, None))
      collection <- collectionService.changeCollectionName(
        MutationsChangeCollectionNameArgs(oldName, newName)
      )
    } yield expect(collection.name === newName)
  }

  test("updateCollectionName errors on inexistant collection") {
    collectionService =>
      for {
        response <-
          collectionService
            .changeCollectionName(
              MutationsChangeCollectionNameArgs(
                "inexistant collection",
                "new name"
              )
            )
            .attempt
      } yield expect(response.isLeft)
  }

  test("updateCollectionName errors if new name already exists") {
    collectionService =>
      val (oldName, newName) = ("another old name", "existing new name")
      for {
        _ <-
          collectionService
            .createCollection(MutationsCreateCollectionArgs(oldName, None))
        _ <-
          collectionService
            .createCollection(MutationsCreateCollectionArgs(newName, None))
        response <-
          collectionService
            .changeCollectionName(
              MutationsChangeCollectionNameArgs(oldName, newName)
            )
            .attempt
      } yield expect(response.isLeft)
  }

  test("addBookToCollection adds book to collection") { collectionService =>
    val name = "collection with books"
    val book = Book("title", List("author"), "cool description", "???", "uri")
    for {
      _ <- collectionService.createCollection(
        MutationsCreateCollectionArgs(name, None)
      )
      collection <-
        collectionService.addBookToCollection(MutationsAddBookArgs(name, book))
    } yield expect(collection === Collection(name, List(book)))
  }

  test("addBookToCollection errors on inexistant collection") {
    collectionService =>
      val name = "inexistant collection"
      val book = Book("title", List("author"), "cool description", "???", "uri")
      for {
        response <-
          collectionService
            .addBookToCollection(
              MutationsAddBookArgs(name, book)
            )
            .attempt
      } yield expect(response.isLeft)
  }

  test("removeBookFromCollection removes book") { collectionService =>
    val name = "collection with books to remove"
    val book = Book("title", List("author"), "cool description", "???", "uri")
    for {
      _ <- collectionService.createCollection(
        MutationsCreateCollectionArgs(name, None)
      )
      _ <-
        collectionService.addBookToCollection(MutationsAddBookArgs(name, book))
      collection <- collectionService.removeBookFromCollection(
        MutationsRemoveBookArgs(name, book.isbn)
      )
    } yield expect(collection === Collection(name, List.empty))
  }

  test("removeBookFromCollection errors on inexistant collection") {
    collectionService =>
      val name = "inexistant collection"
      val isbn = "???"
      for {
        response <-
          collectionService
            .removeBookFromCollection(
              MutationsRemoveBookArgs(name, isbn)
            )
            .attempt
      } yield expect(response.isLeft)
  }

  test("removeBookFromCollection does not error when book not in collection") {
    collectionService =>
      val name = "empty collection"
      val book = Book("title", List("author"), "cool description", "???", "uri")
      for {
        _ <- collectionService.createCollection(
          MutationsCreateCollectionArgs(name, None)
        )
        response <-
          collectionService
            .removeBookFromCollection(
              MutationsRemoveBookArgs(name, book.isbn)
            )
            .attempt
      } yield expect(response.isRight)
  }
}
