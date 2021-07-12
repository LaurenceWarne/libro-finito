package fin.config

import cats.implicits._
import pureconfig._
import pureconfig.generic.semiauto._

import fin.service.{CollectionHook, HookType}

case class ServiceConfig(
    databasePath: String,
    port: Int,
    defaultCollection: Option[String],
    specialCollections: List[SpecialCollection]
)

case class SpecialCollection(
    name: String,
    `lazy`: Option[Boolean],
    addHook: Option[String],
    readStartedHook: Option[String],
    readCompletedHook: Option[String],
    rateHook: Option[String]
) {
  def toCollectionHooks: List[CollectionHook] =
    (addHook.map(CollectionHook(name, HookType.Add, _)) ++
      readStartedHook.map(CollectionHook(name, HookType.ReadStarted, _)) ++
      readCompletedHook.map(CollectionHook(name, HookType.ReadCompleted, _)) ++
      rateHook.map(CollectionHook(name, HookType.Rate, _))).toList
}

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
