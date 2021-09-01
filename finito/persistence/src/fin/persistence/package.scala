package fin.persistence

import java.time.LocalDate
import java.util.Properties

import cats.Functor
import cats.effect.Clock
import cats.implicits._

object DbProperties {

  def properties: Properties = {
    val props = new Properties()
    props.setProperty("connectionInitSql", "PRAGMA foreign_keys=1")
    props
  }
}

object Dates {

  def currentDate[F[_]: Functor](clock: Clock[F]): F[LocalDate] =
    clock.realTime
      .map(fd => LocalDate.ofEpochDay(fd.toDays))
}
