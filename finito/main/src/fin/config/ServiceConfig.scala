package fin.config

import cats.implicits._
import cats.kernel.Eq
import pureconfig._
import pureconfig.generic.semiauto._

import fin.service.collection.{CollectionHook, HookType}

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

final case class SpecialCollection(
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
  implicit val serviceConfigEq: Eq[ServiceConfig] = Eq.fromUniversalEquals

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
