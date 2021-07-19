package fin.persistence

import java.sql.Date
import java.time.{Instant, ZoneId}
import java.util.Properties

object DbProperties {

  def properties: Properties = {
    val props = new Properties()
    props.setProperty("foreign_keys", "true")
    props
  }
}

object DateConversions {
  def instantToDate(instant: Instant): Date =
    Date.valueOf(
      Instant
        .ofEpochMilli(instant.toEpochMilli)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    )

  def dateToInstant(date: Date): Instant =
    Instant.ofEpochMilli(date.getTime)
}
