package fin.persistence

import doobie._
import cats.effect.Blocker
import doobie.hikari._
import cats.effect.Async
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import cats.effect.ContextShift
import scala.concurrent.ExecutionContext

object TransactorSetup {
  def sqliteTransactor[F[_]: Async](
      uri: String,
      ec: ExecutionContext,
      blocker: Blocker
  )(implicit ev: ContextShift[F]): Transactor[F] = {
    val config = new HikariConfig(DbProperties.properties)
    config.setDriverClassName("org.sqlite.JDBC")
    config.setJdbcUrl(uri)
    config.setMaximumPoolSize(4)
    config.setMinimumIdle(2)
    HikariTransactor[F](new HikariDataSource(config), ec, blocker)
  }
}
