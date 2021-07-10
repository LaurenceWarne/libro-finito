package fin

import cats.implicits._
import pureconfig._

case class ServiceConfig(
    databasePath: String,
    port: Int,
    defaultCollectionName: String
)

object ServiceConfig {
  def default(configDirectory: String) =
    ConfigSource.string(
      show"""{
          |  database-path = $configDirectory/db.sqlite,
          |  port = 8080,
          |  default-collection-name = My Books
          |}""".stripMargin
    )
}
