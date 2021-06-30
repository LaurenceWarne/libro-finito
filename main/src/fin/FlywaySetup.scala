package fin

import cats.implicits._
import cats.effect.Sync
import org.flywaydb.core.Flyway

object FlywaySetup {

  def init[F[_]: Sync](uri: String, user: String, password: String): F[Unit] = {
    for {
      flyway <-
        Sync[F].delay(Flyway.configure().dataSource(uri, user, password).load())
      _ <- Sync[F].delay(flyway.migrate())
    } yield ()
  }
}
