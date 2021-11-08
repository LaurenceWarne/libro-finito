package fin.config

import better.files._
import cats.Show
import cats.effect.Sync
import cats.implicits._
import org.typelevel.log4cats.Logger
import pureconfig.ConfigSource

object Config {

  implicit val fileShow: Show[File] = Show.fromToString

  def get[F[_]: Sync: Logger](configDirectoryStr: String): F[ServiceConfig] = {
    val expandedDirectoryStr =
      configDirectoryStr.replaceFirst("^~", File.home.toString)
    for {
      configDirectory <- Sync[F].delay(File(expandedDirectoryStr))
      _               <- Logger[F].info(show"Using config directory $configDirectory")
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

  }
  private def initializeConfigLocation[F[_]: Sync](
      configDirectory: File
  ): F[Unit] =
    Sync[F].delay(configDirectory.createDirectoryIfNotExists()).void
}
