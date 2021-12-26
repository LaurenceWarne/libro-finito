package fin.config

import cats.effect.Async
import cats.implicits._
import fs2.io.file._
import org.typelevel.log4cats.Logger
import pureconfig.ConfigSource

object Config {

  def apply[F[_]: Async: Logger](
      configDirectoryStr: String
  ): F[ServiceConfig] = {
    for {
      home <- Async[F].delay(System.getProperty("user.home"))
      // Java in 2021 :O
      expandedPathStr = configDirectoryStr.replaceFirst("^~", home)
      configDirectory = Path(expandedPathStr).absolute
      _ <- Logger[F].info(show"Using config directory $configDirectory")
      _ <- Files[F].createDirectories(configDirectory)
      configResponse =
        ConfigSource
          .file((configDirectory / "service.conf").toString)
          .optional
          .withFallback(ServiceConfig.default(configDirectory.toString))
          .load[ServiceConfig]
          .leftMap(err => new Exception(err.toString))
      config <- Async[F].fromEither(configResponse)
    } yield config
  }
}
