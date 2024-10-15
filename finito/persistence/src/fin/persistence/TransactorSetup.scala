package fin.persistence

import cats.effect.{Async, Resource}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie._
import doobie.hikari._
import org.typelevel.log4cats.Logger

object TransactorSetup {
  def sqliteTransactor[F[_]: Async: Logger](
      uri: String
  ): Resource[F, Transactor[F]] = {
    val config = new HikariConfig(DbProperties.properties)
    config.setDriverClassName("org.sqlite.JDBC")
    config.setJdbcUrl(uri)
    config.setMaximumPoolSize(4)
    config.setMinimumIdle(2)
    val logHandler = new doobie.LogHandler[F] {
      def run(logEvent: doobie.util.log.LogEvent): F[Unit] =
        Logger[F].debug(logEvent.sql)
    }
    HikariTransactor.fromHikariConfig[F](
      new HikariDataSource(config),
      logHandler = Some(logHandler)
    )
  }
}
