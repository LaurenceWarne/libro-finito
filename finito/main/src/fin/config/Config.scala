package fin.config

import scala.annotation.nowarn

import cats.effect.Async
import cats.syntax.all._
import fs2.io.file._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.parser.decode
import org.typelevel.log4cats.Logger

import fin.service.collection._

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
      configPath = configDirectory / "service.conf"

      configPathExists <- Files[F].exists(configPath)
      (config, msg) <-
        if (configPathExists)
          readUserConfig[F](configDirectory).tupleRight(
            show"Found config file at $configPath"
          )
        else
          Async[F].pure(
            (
              emptyServiceConfig.toServiceConfig(configDirectoryStr),
              show"No config file found at $configPath, using defaults"
            )
          )
      _ <- Logger[F].info(msg)
    } yield config
  }

  private def readUserConfig[F[_]: Async: Logger](
      configDirectory: Path
  ): F[ServiceConfig] = {
    val configPath = configDirectory / "service.conf"
    for {
      configPathExists <- Files[F].exists(configPath)
      _ <- Logger[F].info(
        if (configPathExists) show"Found config file at $configPath"
        else
          show"No config file found at $configPath, defaulting to fallback config"
      )
      configContents <- Files[F].readUtf8(configPath).compile.string
      // Working with typesafe config is such a nightmare 🤮 so we read and then straight encode to
      // JSON and then decode that (it was a mistake using HOCON).
      configObj <- Async[F].delay(
        com.typesafe.config.ConfigFactory.parseString(configContents)
      )
      configStr <- Async[F].delay(
        configObj
          .root()
          .render(
            com.typesafe.config.ConfigRenderOptions.concise()
          )
      )
      configNoDefaults <-
        Async[F].fromEither(decode[ServiceConfigNoDefaults](configStr))
      config = configNoDefaults.toServiceConfig(configDirectory.toString)
      _ <- Logger[F].debug(show"Config: $config")
    } yield config
  }

  private final case class ServiceConfigNoDefaults(
      databasePath: Option[String],
      databaseUser: Option[String],
      databasePassword: Option[String],
      host: Option[String],
      port: Option[Int],
      defaultCollection: Option[String],
      specialCollections: Option[List[SpecialCollection]]
  ) {
    def toServiceConfig(configDirectory: String): ServiceConfig =
      ServiceConfig(
        databasePath.getOrElse(
          ServiceConfig.defaultDatabasePath(configDirectory)
        ),
        databaseUser.getOrElse(ServiceConfig.defaultDatabaseUser),
        databasePassword.getOrElse(ServiceConfig.defaultDatabasePassword),
        host.getOrElse(ServiceConfig.defaultHost),
        port.getOrElse(ServiceConfig.defaultPort),
        defaultCollection,
        specialCollections.getOrElse(ServiceConfig.defaultSpecialCollections)
      )
  }

  private val emptyServiceConfig = ServiceConfigNoDefaults(
    None,
    None,
    None,
    None,
    None,
    None,
    None
  )

  @nowarn private implicit val customConfig: Configuration =
    Configuration.default.withKebabCaseMemberNames.withDefaults
  private implicit val serviceConfigOptionDecoder
      : Decoder[ServiceConfigNoDefaults] =
    deriveConfiguredDecoder
}
