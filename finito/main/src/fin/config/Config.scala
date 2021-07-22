package fin.config

import better.files._
import cats.effect.Sync
import cats.implicits._
import pureconfig.ConfigSource

import File._

object Config {
  def get[F[_]: Sync]: F[ServiceConfig] =
    for {
      configDirectory <- configDirectory
      _               <- initializeConfigLocation(configDirectory)
      configResponse =
        ConfigSource
          .file((configDirectory / "service.conf").toString)
          .optional
          .withFallback(ServiceConfig.default(configDirectory.toString))
          .load[ServiceConfig]
          .leftMap(err => new Exception(err.toString))
      config <- Sync[F].fromEither(configResponse)
    } yield config

  private def initializeConfigLocation[F[_]: Sync](
      configDirectory: File
  ): F[Unit] =
    Sync[F].delay(configDirectory.createDirectoryIfNotExists())

  private def configDirectory[F[_]: Sync]: F[File] =
    Sync[F].delay(home / ".config" / "libro-finito")
}
