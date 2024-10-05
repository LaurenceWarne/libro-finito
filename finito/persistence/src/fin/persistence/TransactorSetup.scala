package fin.persistence

import cats.effect.{Async, Resource}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie._
import doobie.hikari._

object TransactorSetup {
  def sqliteTransactor[F[_]: Async](
      uri: String
  ): Resource[F, Transactor[F]] = {
    val config = new HikariConfig(DbProperties.properties)
    config.setDriverClassName("org.sqlite.JDBC")
    config.setJdbcUrl(uri)
    config.setMaximumPoolSize(4)
    config.setMinimumIdle(2)
    HikariTransactor.fromHikariConfig[F](new HikariDataSource(config))
  }
}
