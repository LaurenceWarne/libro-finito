package fin.service

import cats.MonadError
import cats.implicits._
import io.chrisdavenport.log4cats.Logger

import fin.persistence.CollectionRepository

class DefaultCollectionServiceImpl[F[_]: Logger](
    collectionName: String,
    collectionRepo: CollectionRepository[F]
)(implicit ev: MonadError[F, Throwable])
    extends DefaultCollectionService[F] {

  override def defaultCollectionName: String = collectionName

  override def createDefaultCollection: F[Unit] =
    collectionRepo
      .createCollection(collectionName)
      .handleErrorWith(_ =>
        Logger[F]
          .info(show"Collection '$collectionName' already exists, not creating")
      )
}

object DefaultCollectionServiceImpl {
  def apply[F[_]: Logger](
      collectionName: String,
      collectionRepo: CollectionRepository[F]
  )(implicit ev: MonadError[F, Throwable]) =
    new DefaultCollectionServiceImpl[F](collectionName, collectionRepo)
}
