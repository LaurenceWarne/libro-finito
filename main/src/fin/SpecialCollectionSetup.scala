package fin

import cats.implicits._

import fin.Types._
import fin.config.SpecialCollection
import fin.service._
import cats.effect.Sync
import javax.script.ScriptEngineManager
import io.chrisdavenport.log4cats.Logger

object SpecialCollectionSetup {
  def setup[F[_]: Sync: Logger](
      collectionService: CollectionService[F],
      specialCollections: List[SpecialCollection]
  ): F[CollectionService[F]] =
    for {
      _ <- Logger[F].info(
        "Found special collections: " + specialCollections
          .map(_.name)
          .mkString(", ")
      )
      _ <-
        specialCollections
          .filter(_.`lazy`.contains(false))
          .traverse { collection =>
            Logger[F].info(
              show"Creating collection marked as not lazy: '${collection.name}'"
            ) *>
              collectionService
                .createCollection(
                  MutationsCreateCollectionArgs(collection.name, None)
                )
                .void
                // TODO use error classes here so we don't catch all errors
                .handleErrorWith(_ =>
                  Logger[F]
                    .info(show"Collection '${collection.name}' already exists")
                )
          }
      scriptEngineManager <- Sync[F].delay(new ScriptEngineManager)
      wrappedCollectionService = SpecialCollectionService[F](
        collectionService,
        specialCollections.flatMap(_.toCollectionHooks),
        scriptEngineManager
      )
    } yield wrappedCollectionService
}
