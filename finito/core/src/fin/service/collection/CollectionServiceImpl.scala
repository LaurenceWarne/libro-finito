package fin.service.collection

import java.time.LocalDate

import cats.effect._
import cats.implicits._
import cats.{MonadThrow, ~>}
import natchez.Span

import fin.BookConversions._
import fin.Types._
import fin._
import fin.persistence.{CollectionRepository, Dates}

import CollectionServiceImpl._

class CollectionServiceImpl[F[_]: Async, G[_]: MonadThrow] private (
    collectionRepo: CollectionRepository[G],
    clock: Clock[F],
    transact: G ~> F
) extends CollectionService[F] {

  override def collections(span: Span[F]): F[List[Collection]] =
    span.span("collections").use { _ =>
      transact(collectionRepo.collections)
    }

  override def createCollection(
      args: MutationsCreateCollectionArgs
  ): F[Collection] = {
    val transaction = for {
      maybeExistingCollection <-
        collectionRepo.collection(args.name, None, None)
      sort = Sort(
        args.preferredSortType.getOrElse(defaultSort.`type`),
        args.sortAscending.getOrElse(defaultSort.sortAscending)
      )
      _ <- maybeExistingCollection.fold(
        collectionRepo.createCollection(args.name, sort)
      ) { collection =>
        MonadThrow[G].raiseError(
          CollectionAlreadyExistsError(collection.name)
        )
      }
    } yield Collection(
      args.name,
      args.books.fold(List.empty[UserBook])(_.map(toUserBook(_))),
      sort,
      None
    )
    transact(transaction)
  }

  override def collection(
      args: QueriesCollectionArgs,
      span: Span[F]
  ): F[Collection] =
    span.span("collection").use { _ =>
      transact(
        collectionOrError(
          args.name,
          args.booksPagination.map(_.first),
          args.booksPagination.map(_.after)
        )
      )
    }

  override def deleteCollection(
      args: MutationsDeleteCollectionArgs
  ): F[Unit] = transact(collectionRepo.deleteCollection(args.name))

  override def updateCollection(
      args: MutationsUpdateCollectionArgs
  ): F[Collection] = {
    val transaction = for {
      _ <- MonadThrow[G].raiseUnless(
        List(args.newName, args.preferredSortType, args.sortAscending)
          .exists(_.nonEmpty)
      )(NotEnoughArgumentsForUpdateError)
      collection <- collectionOrError(args.currentName)
      _          <- args.newName.traverse(errorIfCollectionExists)
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
        collection <- collectionOrError(collectionName).ensureOr { c =>
          BookAlreadyInCollectionError(c.name, args.book.title)
        } { c =>
          c.books.forall(_.isbn =!= args.book.isbn)
        }
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

  private def collectionOrError(
      collection: String,
      bookLimit: Option[Int] = None,
      bookOffset: Option[Int] = None
  ): G[Collection] =
    for {
      maybeCollection <-
        collectionRepo.collection(collection, bookLimit, bookOffset)
      collection <- MonadThrow[G].fromOption(
        maybeCollection,
        CollectionDoesNotExistError(collection)
      )
    } yield collection

  private def errorIfCollectionExists(collection: String): G[Unit] =
    collectionRepo
      .collection(collection, None, None)
      .ensure(CollectionAlreadyExistsError(collection))(_.isEmpty)
      .void
}

object CollectionServiceImpl {

  val defaultSort: Sort = Sort(SortType.DateAdded, true)

  def apply[F[_]: Async, G[_]: MonadThrow](
      collectionRepo: CollectionRepository[G],
      clock: Clock[F],
      transact: G ~> F
  ) =
    new CollectionServiceImpl[F, G](collectionRepo, clock, transact)
}
