package fin

import caseapp._

@AppName("Libro Finito")
@AppVersion(BuildInfo.version)
@ProgName("libro-finito")
final case class CliOptions(config: String = "~/.config/libro-finito")
