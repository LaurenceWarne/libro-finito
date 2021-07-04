package fin.service

import fin.Types._

trait CollectionService[F[_]] {
  def collections: F[List[Collection]]
  def createCollection(args: MutationsCreateCollectionArgs): F[Collection]
  def collection(args: QueriesCollectionArgs): F[Collection]
  def deleteCollection(args: MutationsDeleteCollectionArgs): F[Unit]
  def changeCollectionName(
      args: MutationsChangeCollectionNameArgs
  ): F[Collection]
  def addBookToCollection(args: MutationsAddBookArgs): F[Collection]
  def removeBookFromCollection(args: MutationsRemoveBookArgs): F[Unit]
}
