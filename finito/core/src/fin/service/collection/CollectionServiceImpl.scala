package fin.service.collection

import java.time.LocalDate

import cats.effect._
import cats.implicits._
import cats.{MonadThrow, ~>}

import fin.BookConversions._
import fin.Types._
import fin._
import fin.persistence.{CollectionRepository, Dates}

import CollectionServiceImpl._

class CollectionServiceImpl[F[_]: MonadThrow, G[_]: MonadThrow] private (
    collectionRepo: CollectionRepository[G],
    clock: Clock[F],
    transact: G ~> F
) extends CollectionService[F] {

  override def collections: F[List[Collection]] =
    transact(collectionRepo.collections).nested.map(sortBooksFor).value

  override def createCollection(
      args: MutationsCreateCollectionArgs
  ): F[Collection] = {
    val transaction = for {
      maybeExistingCollection <- collectionRepo.collection(args.name)
      _ <- maybeExistingCollection.fold(
        collectionRepo.createCollection(args.name, defaultSort)
      ) { collection =>
        MonadThrow[G].raiseError(
          CollectionAlreadyExistsError(collection.name)
        )
      }
    } yield Collection(
      args.name,
      args.books.fold(List.empty[UserBook])(_.map(toUserBook(_))),
      defaultSort
    )
    transact(transaction)
  }

  override def collection(
      args: QueriesCollectionArgs
  ): F[Collection] = transact(collectionOrError(args.name).map(sortBooksFor))

  override def deleteCollection(
      args: MutationsDeleteCollectionArgs
  ): F[Unit] = transact(collectionRepo.deleteCollection(args.name))

  override def updateCollection(
      args: MutationsUpdateCollectionArgs
  ): F[Collection] = {
    val transaction = for {
      collection <- collectionOrError(args.currentName)
      _ <- MonadThrow[G].raiseUnless(
        List(args.newName, args.preferredSortType, args.sortAscending)
          .exists(_.nonEmpty)
      )(NotEnoughArgumentsForUpdateError)
      _ <- args.newName.traverse(errorIfCollectionExists)
      sort = Sort(
        args.preferredSortType.getOrElse(collection.preferredSort.`type`),
        args.sortAscending.getOrElse(collection.preferredSort.sortAscending)
      )
      _ <- collectionRepo.updateCollection(
        args.currentName,
        args.newName.getOrElse(collection.name),
        sort
      )
    } yield collection.copy(
      name = args.newName.getOrElse(collection.name),
      preferredSort = sort
    )
    transact(transaction)
  }

  override def addBookToCollection(
      args: MutationsAddBookArgs
  ): F[Collection] = {
    val transaction: LocalDate => G[Collection] = date =>
      for {
        collectionName <- MonadThrow[G].fromOption(
          args.collection,
          DefaultCollectionNotSupportedError
        )
        collection <- collectionOrError(collectionName)
        _ <- MonadThrow[G].raiseWhen(
          collection.books.exists(_.isbn === args.book.isbn)
        )(BookAlreadyInCollectionError(collection.name, args.book.title))
        _ <- collectionRepo.addBookToCollection(collectionName, args.book, date)
      } yield collection.copy(books = toUserBook(args.book) :: collection.books)
    Dates.currentDate(clock).flatMap(date => transact(transaction(date)))
  }

  override def removeBookFromCollection(
      args: MutationsRemoveBookArgs
  ): F[Unit] = {
    val transaction =
      for {
        collection <- collectionOrError(args.collection)
        _ <- collectionRepo.removeBookFromCollection(
          args.collection,
          args.isbn
        )
      } yield collection.copy(books =
        collection.books.filterNot(_.isbn === args.isbn)
      )
    transact(transaction).void
  }

  private def collectionOrError(collection: String): G[Collection] =
    for {
      maybeCollection <- collectionRepo.collection(collection)
      collection <- MonadThrow[G].fromOption(
        maybeCollection,
        CollectionDoesNotExistError(collection)
      )
    } yield collection

  private def errorIfCollectionExists(collection: String): G[Unit] =
    for {
      maybeExistingCollection <- collectionRepo.collection(collection)
      _ <- MonadThrow[G].raiseWhen(maybeExistingCollection.nonEmpty)(
        CollectionAlreadyExistsError(collection)
      )
    } yield ()

  def sortBooksFor(collection: Collection): Collection =
    collection.copy(books = collection.books.sortWith {
      case (book1, book2) =>
        val (b1, b2) =
          if (collection.preferredSort.sortAscending) (book1, book2)
          else (book2, book1)
        (collection.preferredSort.`type` match {
          case SortType.DateAdded =>
            b1.dateAdded.map(_.toEpochDay) < b2.dateAdded.map(_.toEpochDay)
          case SortType.Title  => b1.title < b2.title
          case SortType.Author => b1.authors < b2.authors
          case SortType.Rating => b1.rating < b2.rating
        })
    })
}

object CollectionServiceImpl {

  val defaultSort: Sort = Sort(SortType.DateAdded, true)

  def apply[F[_]: MonadThrow, G[_]: MonadThrow](
      collectionRepo: CollectionRepository[G],
      clock: Clock[F],
      transact: G ~> F
  ) =
    new CollectionServiceImpl[F, G](collectionRepo, clock, transact)
}
