package fin.persistence

import cats.implicits._

import fin.BookConversions._
import fin.Types._
import fin.fixtures
import fin.implicits._

object SqliteCollectionRepositoryTest extends SqliteSuite {

  val repo = SqliteCollectionRepository

  testDoobie("collection retrieves created collection") {
    val name = "retrieve_collection"
    for {
      _ <- repo.createCollection(name, Sort(SortType.DateAdded, true))
      retrievedCollection <- repo.collection(name, None, None)
    } yield expect(
      retrievedCollection.exists(
        _ === Collection(
          name,
          List.empty,
          Sort(SortType.DateAdded, true),
          PageInfo(0).some
        )
      )
    )
  }

  testDoobie("collection retrieves collection with date added ordering") {
    val name = "retrieve_collection_with_items_date_added"
    val book2 =
      fixtures.bookInput.copy(title = "added-second", isbn = "added-second")
    val date2 = fixtures.date.plusDays(1)
    for {
      _ <- repo.createCollection(name, Sort(SortType.DateAdded, true))
      _ <- repo.addBookToCollection(name, book2, date2)
      _ <- repo.addBookToCollection(name, fixtures.bookInput, fixtures.date)
      retrievedCollection1 <- repo.collection(name, None, None)
      newSort = Sort(SortType.DateAdded, false)
      _                    <- repo.updateCollection(name, name, newSort)
      retrievedCollection2 <- repo.collection(name, None, None)
    } yield expect(
      retrievedCollection1.exists(
        _ === Collection(
          name,
          List(
            toUserBook(fixtures.bookInput, fixtures.date.some),
            toUserBook(book2, date2.some)
          ),
          Sort(SortType.DateAdded, true),
          PageInfo(2).some
        )
      )
    ) and expect(
      retrievedCollection2.exists(
        _ === Collection(
          name,
          List(
            toUserBook(book2, date2.some),
            toUserBook(fixtures.bookInput, fixtures.date.some)
          ),
          Sort(SortType.DateAdded, false),
          PageInfo(2).some
        )
      )
    )
  }

  // testDoobie("collection retrieves collection with title ordering") {
  //   val name = "retrieve_collection_with_items_title"
  //   val book1 = fixtures.bookInput.copy(
  //     title = "added-first",
  //     isbn = "added-first-title"
  //   )
  //   val book2 = fixtures.bookInput.copy(
  //     title = "added-second",
  //     isbn = "added-second-title"
  //   )
  //   val date2 = fixtures.date.plusDays(1)
  //   for {
  //     _                                 <- repo.createCollection(name, Sort(SortType.Title, true))
  //     _                                 <- repo.addBookToCollection(name, book2, date2)
  //     _                                 <- repo.addBookToCollection(name, book1, fixtures.date)
  //     retrievedCollectionAscendingOrder <- repo.collection(name, None, None)
  //     newSort = Sort(SortType.Title, false)
  //     _                                  <- repo.updateCollection(name, name, newSort)
  //     retrievedCollectionDescendingOrder <- repo.collection(name, None, None)
  //   } yield expect(
  //     retrievedCollectionAscendingOrder.exists(
  //       _ === Collection(
  //         name,
  //         List(
  //           toUserBook(book1, fixtures.date.some),
  //           toUserBook(book2, date2.some)
  //         ),
  //         Sort(SortType.Title, true),
  //         PageInfo(2).some
  //       )
  //     )
  //   ) and expect(
  //     retrievedCollectionDescendingOrder.exists(
  //       _ === Collection(
  //         name,
  //         List(
  //           toUserBook(book2, date2.some),
  //           toUserBook(book1, fixtures.date.some)
  //         ),
  //         Sort(SortType.Title, false),
  //         PageInfo(2).some
  //       )
  //     )
  //   )
  // }

  testDoobie("collection limit offset") {
    val name = "retrieve_collection_with_lots_of_items"
    val books = (1 to 9)
      .map(i =>
        fixtures.bookInput.copy(isbn = show"book-no-$i", title = i.toString())
      )
      .toList
    val (limit, offset) = (5, 2)
    for {
      _ <- repo.createCollection(name, Sort(SortType.Title, true))
      _ <- books.traverse(b => repo.addBookToCollection(name, b, fixtures.date))
      retrievedCollection <- repo.collection(name, limit.some, offset.some)
    } yield expect(
      retrievedCollection.exists(
        _ === Collection(
          name,
          books.drop(offset).take(limit).map(toUserBook(_, fixtures.date.some)),
          Sort(SortType.Title, true),
          PageInfo(9).some
        )
      )
    )
  }

  testDoobie("createCollection fails when name already exists") {
    val name = "duplicated_name"
    for {
      _ <- repo.createCollection(name, Sort(SortType.DateAdded, true))
      response <-
        repo.createCollection(name, Sort(SortType.DateAdded, true)).attempt
    } yield expect(response.isLeft)
  }

  testDoobie("collections retrieves created collections") {
    val (name1, name2, name3) = ("collection1", "collection2", "collection3")
    for {
      _ <- repo.createCollection(name1, Sort(SortType.DateAdded, true))
      _ <- repo.createCollection(name2, Sort(SortType.DateAdded, true))
      _ <- repo.createCollection(name3, Sort(SortType.DateAdded, true))
      retrievedCollections <- repo.collections
    } yield expect(
      Set(name1, name2, name3).subsetOf(retrievedCollections.map(_.name).toSet)
    )
  }

  testDoobie("updateCollection changes collection name and sort") {
    val oldName = "old_name"
    val newName = "new_name"
    val oldSort = Sort(SortType.DateAdded, true)
    val newSort = Sort(SortType.Title, true)
    for {
      _                   <- repo.createCollection(oldName, oldSort)
      _                   <- repo.updateCollection(oldName, newName, newSort)
      retrievedCollection <- repo.collection(newName, None, None)
    } yield expect(
      retrievedCollection.exists(c =>
        c.name === newName && c.preferredSort === newSort
      )
    )
  }

  testDoobie("updateCollection changes only sort") {
    val name    = "name with sort to change"
    val oldSort = Sort(SortType.DateAdded, true)
    val newSort = Sort(SortType.Title, false)
    for {
      _                   <- repo.createCollection(name, oldSort)
      _                   <- repo.updateCollection(name, name, newSort)
      retrievedCollection <- repo.collection(name, None, None)
    } yield expect(
      retrievedCollection.exists(c =>
        c.name === name && c.preferredSort === newSort
      )
    )
  }

  testDoobie(
    "updateCollection changes collection name and sort for collection with books"
  ) {
    val oldName = "old_name with books"
    val newName = "new_name with books"
    val oldSort = Sort(SortType.DateAdded, true)
    val newSort = Sort(SortType.Title, true)
    for {
      _ <- repo.createCollection(oldName, oldSort)
      _ <- repo.addBookToCollection(oldName, fixtures.bookInput, fixtures.date)
      _ <- repo.updateCollection(oldName, newName, newSort)
      retrievedCollection <- repo.collection(newName, None, None)
    } yield expect(
      retrievedCollection.exists(c =>
        c === Collection(
          newName,
          List(toUserBook(fixtures.bookInput, dateAdded = fixtures.date.some)),
          newSort,
          PageInfo(1).some
        )
      )
    )
  }

  testDoobie("updateCollection errors if name already exists") {
    val oldName = "old_name_"
    val newName = "new_name_"
    val sort    = Sort(SortType.DateAdded, true)
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
      response <-
        repo
          .updateCollection(name, "???", Sort(SortType.DateAdded, true))
          .attempt
    } yield expect(response.isRight)
  }

  testDoobie("AddToCollection adds book not already added") {
    val name = "collection with books"
    val sort = Sort(SortType.DateAdded, true)
    for {
      _ <- repo.createCollection(name, sort)
      _ <- repo.addBookToCollection(name, fixtures.bookInput, fixtures.date)
      retrievedCollection <- repo.collection(name, None, None)
    } yield expect(
      retrievedCollection.exists(
        _ === Collection(
          name,
          List(toUserBook(fixtures.bookInput, dateAdded = fixtures.date.some)),
          sort,
          PageInfo(1).some
        )
      )
    )
  }

  testDoobie("AddToCollection adds in another collection") {
    val name1 = "collection with books 1"
    val name2 = "collection with books 2"
    val sort  = Sort(SortType.DateAdded, true)
    for {
      _ <- repo.createCollection(name1, sort)
      _ <- repo.createCollection(name2, sort)
      _ <- repo.addBookToCollection(name1, fixtures.bookInput, fixtures.date)
      _ <- repo.addBookToCollection(name2, fixtures.bookInput, fixtures.date)
      retrievedCollection <- repo.collection(name2, None, None)
    } yield expect(
      retrievedCollection.exists(
        _ === Collection(
          name2,
          List(toUserBook(fixtures.bookInput, dateAdded = fixtures.date.some)),
          sort,
          PageInfo(1).some
        )
      )
    )
  }

  testDoobie("AddToCollection errors if collection does not exist") {
    val name = "inexistant collection #2"
    for {
      response <-
        repo
          .addBookToCollection(name, fixtures.bookInput, fixtures.date)
          .attempt
    } yield expect(response.isLeft)
  }

  testDoobie("deleteCollection successful with collection with no books") {
    val name = "collection to delete"
    for {
      _ <- repo.createCollection(name, Sort(SortType.DateAdded, true))
      _ <- repo.deleteCollection(name)
      maybeCollection <- repo.collection(name, None, None)
    } yield expect(maybeCollection.isEmpty)
  }

  testDoobie("deleteCollection successful with collection with one book") {
    val name  = "collection to delete with books"
    val book2 = fixtures.bookInput.copy(isbn = "isbn-d")
    for {
      _ <- repo.createCollection(name, Sort(SortType.DateAdded, true))
      _ <- repo.addBookToCollection(name, book2, fixtures.date)
      _ <- repo.deleteCollection(name)
      maybeCollection <- repo.collection(name, None, None)
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
    val book2 = fixtures.bookInput.copy(isbn = "isbn-d")
    for {
      _ <- repo.createCollection(name, Sort(SortType.DateAdded, true))
      _ <- repo.addBookToCollection(name, book2, fixtures.date)
      _ <- repo.removeBookFromCollection(name, book2.isbn)
      maybeCollection <- repo.collection(name, None, None)
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
      _        <- repo.createCollection(name, Sort(SortType.DateAdded, true))
      response <- repo.removeBookFromCollection(name, isbn).attempt
    } yield expect(response.isRight)
  }
}
