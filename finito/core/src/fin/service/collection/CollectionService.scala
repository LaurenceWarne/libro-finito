package fin.service.collection

import fin.Types._

trait CollectionService[F[_]] {
  def collections: F[List[Collection]]
  def createCollections(names: Set[String]): F[List[Collection]]
  def createCollection(args: MutationCreateCollectionArgs): F[Collection]
  def collection(args: QueryCollectionArgs): F[Collection]
  def deleteCollection(args: MutationDeleteCollectionArgs): F[Unit]
  def updateCollection(args: MutationUpdateCollectionArgs): F[Collection]
  def addBookToCollection(args: MutationAddBookArgs): F[Collection]
  def removeBookFromCollection(args: MutationRemoveBookArgs): F[Unit]
}
