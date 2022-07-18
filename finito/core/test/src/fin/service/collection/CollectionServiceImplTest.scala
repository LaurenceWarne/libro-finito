package fin.service.collection

import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import weaver._

import fin.BookConversions._
import fin.Types._
import fin._
import fin.implicits._

object CollectionServiceImplTest extends IOSuite {

  val book =
    BookInput(
      "title",
      List("author"),
      "cool description",
      "???",
      "uri"
    )

  override type Res = CollectionService[IO]
  override def sharedResource: Resource[IO, CollectionService[IO]] =
    Resource.eval(Ref.of[IO, List[Collection]](List.empty).map { ref =>
      CollectionServiceImpl[IO, IO](
        new InMemoryCollectionRepository(ref),
        Clock[IO],
        FunctionK.id[IO]
      )
    })

  test("createCollection creates collection") { collectionService =>
    val name = "name"
    val sort = Sort(SortType.Author, false)
    for {
      collection <- collectionService.createCollection(
        MutationsCreateCollectionArgs(
          name,
          None,
          sort.`type`.some,
          sort.sortAscending.some
        )
      )
    } yield expect(collection.name === name) and expect(
      collection.preferredSort === sort
    )
  }

  test("createCollection errors if collection already exists") {
    collectionService =>
      val name = "already existing"
      for {
        _ <- collectionService.createCollection(
          MutationsCreateCollectionArgs(name, None, None, None)
        )
        response <-
          collectionService
            .createCollection(
              MutationsCreateCollectionArgs(name, None, None, None)
            )
            .attempt
      } yield expect(response == CollectionAlreadyExistsError(name).asLeft)
  }

  test("collection returns created collection") { collectionService =>
    val name = "name to retrieve"
    for {
      _ <- collectionService.createCollection(
        MutationsCreateCollectionArgs(name, None, None, None)
      )
      retrievedCollection <-
        collectionService.collection(QueriesCollectionArgs(name, None))
    } yield expect(retrievedCollection.name === name)
  }

  test("collection errors on inexistant collection") { collectionService =>
    val name = "not a collection!"
    for {
      response <-
        collectionService
          .collection(QueriesCollectionArgs(name, None))
          .attempt
    } yield expect(response == CollectionDoesNotExistError(name).asLeft)
  }

  test("collections returns created collections") { collectionService =>
    val (name1, name2) = ("name1", "name2")
    for {
      _ <- collectionService.createCollection(
        MutationsCreateCollectionArgs(name1, None, None, None)
      )
      _ <- collectionService.createCollection(
        MutationsCreateCollectionArgs(name2, None, None, None)
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
        MutationsCreateCollectionArgs(name, None, None, None)
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

  test("updateCollection udpates collection name and sort") {
    collectionService =>
      val (oldName, newName)           = ("old name", "new name")
      val (newSortType, sortAscending) = (SortType.Author, true)
      for {
        _ <-
          collectionService
            .createCollection(
              MutationsCreateCollectionArgs(oldName, None, None, None)
            )
        collection <- collectionService.updateCollection(
          MutationsUpdateCollectionArgs(
            oldName,
            newName.some,
            newSortType.some,
            sortAscending.some
          )
        )
      } yield expect(collection.name === newName) and expect(
        collection.preferredSort === Sort(newSortType, sortAscending)
      )
  }

  test("updateCollection udpates collection sort asc/desc") {
    collectionService =>
      val name          = "collection with sort asc to update"
      val sortAscending = false
      for {
        _ <-
          collectionService
            .createCollection(
              MutationsCreateCollectionArgs(name, None, None, None)
            )
        collection <- collectionService.updateCollection(
          MutationsUpdateCollectionArgs(
            name,
            None,
            None,
            sortAscending.some
          )
        )
      } yield expect(collection.preferredSort.sortAscending === false)
  }

  test("updateCollection errors on inexistant collection") {
    collectionService =>
      val name = "inexistant collection"
      for {
        response <-
          collectionService
            .updateCollection(
              MutationsUpdateCollectionArgs(
                name,
                "new name".some,
                None,
                None
              )
            )
            .attempt
      } yield expect(response == CollectionDoesNotExistError(name).asLeft)
  }

  test("updateCollection errors if new name already exists") {
    collectionService =>
      val (oldName, newName) = ("another old name", "existing new name")
      for {
        _ <-
          collectionService
            .createCollection(
              MutationsCreateCollectionArgs(oldName, None, None, None)
            )
        _ <-
          collectionService
            .createCollection(
              MutationsCreateCollectionArgs(newName, None, None, None)
            )
        response <-
          collectionService
            .updateCollection(
              MutationsUpdateCollectionArgs(
                oldName,
                newName.some,
                None,
                None
              )
            )
            .attempt
      } yield expect(response == CollectionAlreadyExistsError(newName).asLeft)
  }

  test("updateCollection errors if all arguments None") { collectionService =>
    val name = "another name"
    for {
      _ <-
        collectionService
          .createCollection(
            MutationsCreateCollectionArgs(name, None, None, None)
          )
      response <-
        collectionService
          .updateCollection(
            MutationsUpdateCollectionArgs(name, None, None, None)
          )
          .attempt
    } yield expect(response == NotEnoughArgumentsForUpdateError.asLeft)
  }

  test("addBookToCollection adds book to collection") { collectionService =>
    val name = "collection with books"
    for {
      _ <- collectionService.createCollection(
        MutationsCreateCollectionArgs(name, None, None, None)
      )
      collection <- collectionService.addBookToCollection(
        MutationsAddBookArgs(name.some, book)
      )
    } yield expect(
      collection === Collection(
        name,
        List(toUserBook(book)),
        CollectionServiceImpl.defaultSort
      )
    )
  }

  test("addBookToCollection errors on inexistant collection") {
    collectionService =>
      val name = "inexistant collection"
      for {
        response <-
          collectionService
            .addBookToCollection(
              MutationsAddBookArgs(name.some, book)
            )
            .attempt
      } yield expect(response == CollectionDoesNotExistError(name).asLeft)
  }

  test("addBookToCollection errors when default collection not set") {
    collectionService =>
      for {
        response <-
          collectionService
            .addBookToCollection(MutationsAddBookArgs(None, book))
            .attempt
      } yield expect(response == DefaultCollectionNotSupportedError.asLeft)
  }

  test("addBookToCollection errors when book already in collection") {
    collectionService =>
      val name = "collection with double book"
      for {
        _ <-
          collectionService
            .createCollection(
              MutationsCreateCollectionArgs(name, None, None, None)
            )
        _ <-
          collectionService
            .addBookToCollection(MutationsAddBookArgs(name.some, book))
        response <-
          collectionService
            .addBookToCollection(MutationsAddBookArgs(name.some, book))
            .attempt
      } yield expect(
        response == BookAlreadyInCollectionError(name, book.title).asLeft
      )
  }

  test("removeBookFromCollection removes book") { collectionService =>
    val name = "collection with books to remove"
    for {
      _ <- collectionService.createCollection(
        MutationsCreateCollectionArgs(name, None, None, None)
      )
      _ <- collectionService.addBookToCollection(
        MutationsAddBookArgs(name.some, book)
      )
      _ <- collectionService.removeBookFromCollection(
        MutationsRemoveBookArgs(name, book.isbn)
      )
      retrievedCollection <-
        collectionService.collection(QueriesCollectionArgs(name, None))
    } yield expect(retrievedCollection.books.isEmpty)
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
      } yield expect(response == CollectionDoesNotExistError(name).asLeft)
  }

  test("removeBookFromCollection does not error when book not in collection") {
    collectionService =>
      val name = "empty collection"
      for {
        _ <- collectionService.createCollection(
          MutationsCreateCollectionArgs(name, None, None, None)
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
