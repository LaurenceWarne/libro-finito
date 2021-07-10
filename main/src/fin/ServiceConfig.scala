package fin

import cats.implicits._
import pureconfig._
import pureconfig.generic.semiauto._

case class ServiceConfig(
    databasePath: String,
    port: Int,
    defaultCollection: Option[String],
    specialCollections: List[SpecialCollection]
)

case class SpecialCollection(
    name: String,
    `lazy`: Option[String],
    addHook: Option[String],
    readStartedHook: Option[String],
    readCompletedHook: Option[String],
    rateHook: Option[String]
)

object ServiceConfig {
  implicit val collectionReader: ConfigReader[SpecialCollection] =
    deriveReader[SpecialCollection]
  implicit val confReader: ConfigReader[ServiceConfig] =
    deriveReader[ServiceConfig]

  def default(configDirectory: String) =
    ConfigSource.string(
      show"""{
          |  database-path = $configDirectory/db.sqlite,
          |  port = 8080,
          |  default-collection-name = My Books,
          |  special-collections = [
          |    {
          |      name = My Books,
          |      lazy = false,
          |      add-hook = \"\"\"add = true\"\"\"
          |    },
          |    {
          |      name = Currently Reading,
          |      read-begun-hook = \"\"\"add = true\"\"\",
          |      read-complete-hook = \"\"\"remove = true\"\"\"
          |    },
          |    {
          |      name = Favourites,
          |      rate-hook = \"\"\"
          |        if(rating == 5) then
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
