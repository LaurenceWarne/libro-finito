package fin

import scala.annotation.nowarn

import cats.effect.Async
import cats.effect.std.Env
import cats.syntax.all._
import fs2.io.file._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.parser.decode
import org.typelevel.log4cats.Logger

import fin.config.ServiceConfig
import fin.service.collection._

object FinitoFiles {

  private val FinitoDir = "libro-finito"

  def databaseUri[F[_]: Async: Logger](env: Env[F]): F[String] = {
    val dbName = "db.sqlite"
    for {
      xdgDataDir <- xdgDirectory(env, "XDG_DATA_HOME", ".local/share")
      dbPath = xdgDataDir / dbName

      finitoDataDirExists <- Files[F].exists(xdgDataDir)
      _ <- Async[F].unlessA(finitoDataDirExists) {
        Files[F].createDirectories(xdgDataDir) *>
          xdgConfigDirectory(env).flatMap { confPath =>
            val deprecatedPath = confPath / dbName
            Async[F].ifM(Files[F].exists(deprecatedPath))(
              Files[F].move(deprecatedPath, dbPath) *> Logger[F].info(
                show"Moved db in config directory '$deprecatedPath' to new path '$dbPath' (see https://specifications.freedesktop.org/basedir-spec/latest/index.html for more information)"
              ),
              Async[F].unit
            )
          }
      }
      _ <- Logger[F].info(show"Using data directory $xdgDataDir")
    } yield s"jdbc:sqlite:${dbPath.absolute}"
  }

  def config[F[_]: Async: Logger](env: Env[F]): F[ServiceConfig] =
    for {
      configDir <- xdgConfigDirectory(env)
      _         <- Logger[F].info(show"Using config directory $configDir")
      _         <- Files[F].createDirectories(configDir)
      configPath = configDir / "service.conf"

      configPathExists <- Files[F].exists(configPath)
      (config, msg) <-
        if (configPathExists)
          readUserConfig[F](configPath).tupleRight(
            show"Found config file at $configPath"
          )
        else
          Async[F].pure(
            (
              emptyServiceConfig.toServiceConfig(configExists = false),
              show"No config file found at $configPath, using defaults"
            )
          )
      _ <- Logger[F].info(msg)
    } yield config

  private def xdgConfigDirectory[F[_]: Async](env: Env[F]): F[Path] =
    xdgDirectory(env, "XDG_CONFIG_HOME", ".config")

  private def xdgDirectory[F[_]: Async](
      env: Env[F],
      envVar: String,
      fallback: String
  ): F[Path] = {
    lazy val fallbackConfigDir = Async[F]
      .delay(System.getProperty("user.home"))
      .map(s => Path(s) / fallback)

    env
      .get(envVar)
      .flatMap { opt =>
        opt.fold(fallbackConfigDir)(s => Async[F].pure(Path(s)))
      }
      .map(path => (path / FinitoDir).absolute)
  }

  private def readUserConfig[F[_]: Async: Logger](
      configPath: Path
  ): F[ServiceConfig] = {
    for {
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
      config = configNoDefaults.toServiceConfig(
        configExists = true
      )
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
    def toServiceConfig(
        configExists: Boolean
    ): ServiceConfig =
      ServiceConfig(
        databaseUser =
          databaseUser.getOrElse(ServiceConfig.defaultDatabaseUser),
        databasePassword =
          databasePassword.getOrElse(ServiceConfig.defaultDatabasePassword),
        host = host.getOrElse(ServiceConfig.defaultHost),
        port = port.getOrElse(ServiceConfig.defaultPort),
        // The only case when we don't set a default collection is when a config file exists
        // and it doesn't specify a default collection.
        defaultCollection =
          if (configExists)
            defaultCollection
          else
            Some(ServiceConfig.defaultDefaultCollection),
        specialCollections =
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
