package fin.config

import cats.Show
import cats.kernel.Eq

import fin.service.collection._

final case class ServiceConfig(
    databaseUser: String,
    databasePassword: String,
    host: String,
    port: Int,
    defaultCollection: Option[String],
    specialCollections: List[SpecialCollection]
)

object ServiceConfig {
  implicit val serviceConfigEq: Eq[ServiceConfig] =
    Eq.fromUniversalEquals[ServiceConfig]
  implicit val serviceConfigShow: Show[ServiceConfig] =
    Show.fromToString[ServiceConfig]

  val defaultDatabaseUser: String      = ""
  val defaultDatabasePassword: String  = ""
  val defaultHost: String              = "127.0.0.1"
  val defaultPort: Int                 = 56848
  val defaultDefaultCollection: String = "My Books"
  val defaultSpecialCollections: List[SpecialCollection] = List(
    SpecialCollection(
      name = "My Books",
      `lazy` = Some(false),
      addHook = Some("add = true"),
      readStartedHook = Some("add = true"),
      readCompletedHook = Some("add = true"),
      rateHook = Some("add = true"),
      preferredSort = None
    ),
    SpecialCollection(
      name = "Currently Reading",
      `lazy` = Some(true),
      addHook = None,
      readStartedHook = Some("add = true"),
      readCompletedHook = Some("remove = true"),
      rateHook = None,
      preferredSort = None
    ),
    SpecialCollection(
      name = "Read",
      `lazy` = Some(true),
      addHook = None,
      readStartedHook = None,
      readCompletedHook = Some("add = true"),
      rateHook = None,
      preferredSort = None
    ),
    SpecialCollection(
      name = "Favourites",
      `lazy` = Some(true),
      addHook = None,
      readStartedHook = None,
      readCompletedHook = None,
      rateHook = Some("""
                      |if(rating >= 5) then
                      |  add = true
                      |else
                      |  remove = true
                      |end""".stripMargin),
      preferredSort = None
    )
  )
}
