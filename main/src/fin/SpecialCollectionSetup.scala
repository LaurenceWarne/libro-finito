package fin

import cats.Applicative
import cats.implicits._

import fin.Types._
import fin.config.SpecialCollection
import fin.service._

object SpecialCollectionSetup {
  def setup[F[_]: Applicative](
      collectionService: CollectionService[F],
      specialCollections: List[SpecialCollection]
  ): F[Unit] =
    specialCollections
      .filter(_.`lazy`.contains(false))
      .traverse { collection =>
        collectionService.createCollection(
          MutationsCreateCollectionArgs(collection.name, None)
        )
      }
      .void
}
