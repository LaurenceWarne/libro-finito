package fin.persistence

import java.time.LocalDate
import java.util.Properties

import scala.concurrent.duration.DAYS

import cats.Functor
import cats.effect.Clock
import cats.implicits._

object DbProperties {

  def properties: Properties = {
    val props = new Properties()
    props.setProperty("foreign_keys", "true")
    props
  }
}

object Dates {

  def currentDate[F[_]: Functor](clock: Clock[F]): F[LocalDate] =
    clock
      .realTime(DAYS)
      .map(LocalDate.ofEpochDay(_))
}
