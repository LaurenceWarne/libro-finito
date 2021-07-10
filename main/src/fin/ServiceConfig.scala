package fin

import cats.implicits._
import pureconfig._

case class ServiceConfig(
    databasePath: String,
    port: Int,
    enableDefaultCollection: Boolean,
    defaultCollectionName: String,
    addAllBooksToDefaultCollection: Boolean
)

object ServiceConfig {
  def default(configDirectory: String) =
    ConfigSource.string(
      show"""{
          |  database-path = $configDirectory/db.sqlite,
          |  port = 8080,
          |  enable-default-collection = true,
          |  default-collection-name = My Books,
          |  add-all-books-to-default-collection = true
          |}""".stripMargin
    )
}
