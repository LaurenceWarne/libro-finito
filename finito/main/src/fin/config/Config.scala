package fin.config

import better.files._
import cats.effect.Sync
import cats.implicits._
import pureconfig.ConfigSource

object Config {
  def get[F[_]: Sync](configDirectoryStr: String): F[ServiceConfig] = {
    val configDirectory = File(configDirectoryStr)
    for {
      _ <- initializeConfigLocation(configDirectory)
      configResponse =
        ConfigSource
          .file((configDirectory / "service.conf").toString)
          .optional
          .withFallback(ServiceConfig.default(configDirectory.toString))
          .load[ServiceConfig]
          .leftMap(err => new Exception(err.toString))
      config <- Sync[F].fromEither(configResponse)
    } yield config

  }
  private def initializeConfigLocation[F[_]: Sync](
      configDirectory: File
  ): F[Unit] =
    Sync[F].delay(configDirectory.createDirectoryIfNotExists())
}
