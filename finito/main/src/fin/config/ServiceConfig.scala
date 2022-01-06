package fin.config

import cats.implicits._
import cats.kernel.Eq
import pureconfig._
import pureconfig.generic.semiauto._

import fin.Types._
import fin.service.collection._

final case class ServiceConfig(
    databasePath: String,
    databaseUser: String,
    databasePassword: String,
    host: String,
    port: Int,
    defaultCollection: Option[String],
    specialCollections: List[SpecialCollection]
) {
  def databaseUri: String = show"jdbc:sqlite:$databasePath"
}

object ServiceConfig {
  implicit val sortTypeReader   = deriveEnumerationReader[SortType]
  implicit val sortReader       = deriveReader[Sort]
  implicit val collectionReader = deriveReader[SpecialCollection]
  implicit val confReader       = deriveReader[ServiceConfig]
  implicit val serviceConfigEq  = Eq.fromUniversalEquals[ServiceConfig]

  val defaultPort: Int = 56848

  def default(configDirectory: String): ConfigObjectSource =
    ConfigSource.string(
      show"""{
          |  database-path = $configDirectory/db.sqlite,
          |  database-user = "",
          |  database-password = "",
          |  host = "0.0.0.0",
          |  port = $defaultPort,
          |  default-collection = My Books,
          |  special-collections = [
          |    {
          |      name = My Books,
          |      lazy = false,
          |      add-hook = \"\"\"add = true\"\"\",
          |      read-started-hook = \"\"\"add = true\"\"\",
          |      read-completed-hook = \"\"\"add = true\"\"\",
          |      rate-hook = \"\"\"add = true\"\"\"
          |    },
          |    {
          |      name = Currently Reading,
          |      sort = {
          |        type = last-read,
          |        sort-ascending = false
          |      },
          |      read-started-hook = \"\"\"add = true\"\"\",
          |      read-completed-hook = \"\"\"remove = true\"\"\"
          |    },
          |    {
          |      name = Read,
          |      read-completed-hook = \"\"\"add = true\"\"\"
          |    },
          |    {
          |      name = Favourites,
          |      rate-hook = \"\"\"
          |        if(rating >= 5) then
          |          add = true
          |        else
          |          remove = true
          |        end
          |      \"\"\"
          |    }
          |  ]
          |}""".stripMargin
    )

}
