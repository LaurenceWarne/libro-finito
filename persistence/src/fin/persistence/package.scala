package fin.persistence

import java.util.Properties

object DbProperties {

  def properties: Properties = {
    val props = new Properties()
    props.setProperty("foreign_keys", "true")
    props
  }
}
