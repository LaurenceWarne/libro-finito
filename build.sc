// build.sc
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:$MILL_VERSION`
import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`
import $ivy.`com.goyeau::mill-scalafix:0.2.4`
import $file.plugins.calibanSchemaGen
import mill._, scalalib._, scalafmt._
import mill.contrib.buildinfo.BuildInfo
import mill.contrib.scoverage.{ScoverageModule, ScoverageReport}
import mill.eval.Evaluator
import com.goyeau.mill.scalafix.ScalafixModule
import coursier.maven.MavenRepository
import calibanSchemaGen.CalibanSchemaModule

object main extends LibroFinitoModule with BuildInfo {

  def version = "0.0.1"

  def buildInfoPackageName = Some("fin")

  def buildInfoMembers: T[Map[String, String]] =
    T {
      Map(
        "version"      -> version,
        "scalaVersion" -> scalaVersion()
      )
    }

  def moduleDeps = Seq(api, core, persistence)

  //def forkArgs   = Seq("-Xmx100m")

  def scalacPluginIvyDeps =
    super.scalacPluginIvyDeps() ++ Agg(Deps.Compiler.betterMonadicFor)
  def ivyDeps =
    Agg(
      Deps.betterFiles,
      Deps.Caliban.cats,
      Deps.Caliban.core,
      Deps.Caliban.http4s,
      Deps.Circe.core,
      Deps.Circe.generic,
      Deps.Circe.parser,
      Deps.Doobie.core,
      Deps.Http4s.http4sBlazeClient,
      Deps.Http4s.http4sBlazeServer,
      Deps.Http4s.http4sDsl,
      Deps.catsEffect,
      Deps.catsLogging,
      Deps.catsLoggingCore,
      Deps.flyway,
      Deps.logback,
      Deps.pureconfig
    )
}

object api extends LibroFinitoModuleNoLinting with CalibanSchemaModule {

  def schemaPath         = "schema.gql"
  def packageName        = "fin"
  def abstractEffectType = true
  def scalarMappings     = Map("DateTime" -> "java.time.Instant")

  def ivyDeps =
    Agg(
      Deps.Caliban.core,
      Deps.Caliban.cats,
      Deps.catsEffect
    )
}

object core extends LibroFinitoModule {

  def moduleDeps = Seq(api, persistence)

  def ivyDeps =
    Agg(
      Deps.Caliban.cats,
      Deps.Caliban.core,
      Deps.Caliban.http4s,
      Deps.Circe.core,
      Deps.Circe.generic,
      Deps.Circe.parser,
      Deps.enumeratum,
      Deps.Http4s.http4sBlazeClient,
      Deps.Http4s.http4sBlazeServer,
      Deps.Http4s.http4sDsl,
      Deps.catsEffect,
      Deps.catsLogging,
      Deps.luaj
    )

  object test extends Tests with ScoverageTests with LibroFinitoTest
}

object persistence extends LibroFinitoModule {

  def moduleDeps = Seq(api)

  def ivyDeps =
    Agg(
      Deps.catsEffect,
      Deps.catsLogging,
      Deps.Circe.core,
      Deps.Circe.generic,
      Deps.Circe.parser,
      Deps.Doobie.core,
      Deps.flyway,
      Deps.sqlite
    )

  object test extends Tests with ScoverageTests with LibroFinitoTest
}

// TODO use this when https://github.com/com-lihaoyi/mill/pull/1309 is merged
// object scoverage extends ScoverageReport {
//   def scalaVersion     = Deps.scalaVersion
//   def scoverageVersion = Deps.scoverageVersion
// }

trait LibroFinitoModuleNoLinting extends ScalaModule with ScoverageModule {
  def scalaVersion = Deps.scalaVersion
  // https://github.com/com-lihaoyi/mill/blob/main/docs/antora/modules/ROOT/pages/Contrib_Modules.adoc#scoverage
  def scoverageVersion = Deps.scoverageVersion
  def scalacOptions    = Options.scalacOptions

  // Since compiler plugins are not backwards compatible with scala patches,
  // the scoverage dep plugin is not published along scala minor versions
  // but mill currently uses "::" instead of ":::" which grabs an out of
  // date (and binary incompatible!) scoverage plugin version:
  // org.scoverage:::scalac-scoverage-plugin_2.13, but we want:
  // org.scoverage:::scalac-scoverage-plugin_.2.13.6
  // https://github.com/com-lihaoyi/mill/pull/1309 should remove the need for this
  def scoveragePluginDep =
    ivy"org.scoverage:::scalac-scoverage-plugin:${scoverageVersion()}"

  def repositories =
    super.repositories ++ Seq(
      MavenRepository("https://jitpack.io")
    )
}

trait LibroFinitoModule
    extends LibroFinitoModuleNoLinting
    with ScalafmtModule
    with ScalafixModule {
  def scalafixIvyDeps = Agg(Deps.Scalafix.organizeImports)
}

trait LibroFinitoTest
    extends TestModule
    with ScalafmtModule
    with ScalafixModule {
  def scalafixIvyDeps = Agg(Deps.Scalafix.organizeImports)
  def scalacOptions   = Options.scalacOptions

  def ivyDeps = Agg(Deps.weaver)
  // https://github.com/disneystreaming/weaver-test
  def testFramework = "weaver.framework.CatsEffect"
}

object Options {
  val scalacOptions = Seq("-deprecation", "-Ywarn-unused", "-Xfatal-warnings")
}

object Deps {
  val scalaVersion     = "2.13.6"
  val scoverageVersion = "1.4.8"
  val catsEffect       = ivy"org.typelevel::cats-effect:2.5.0"
  val catsLoggingCore  = ivy"io.chrisdavenport::log4cats-core:1.1.1"
  val catsLogging      = ivy"io.chrisdavenport::log4cats-slf4j:1.1.1"
  val logback          = ivy"ch.qos.logback:logback-classic:1.1.3"
  val weaver           = ivy"com.disneystreaming::weaver-cats:0.6.4"
  val sqlite           = ivy"org.xerial:sqlite-jdbc:3.34.0"
  val flyway           = ivy"org.flywaydb:flyway-core:7.10.0"
  val pureconfig       = ivy"com.github.pureconfig::pureconfig:0.16.0"
  val betterFiles      = ivy"com.github.pathikrit::better-files:3.9.1"
  val enumeratum       = ivy"com.beachape::enumeratum:1.7.0"
  // https://github.com/luaj/luaj/issues/91 ):
  val luaj = ivy"org.luaj:luaj-jse:3.0.1"

  object Compiler {
    val semanticDb       = ivy"org.scalameta::semanticdb-scalac:4.4.22"
    val betterMonadicFor = ivy"com.olegpy::better-monadic-for:0.3.1"
  }

  object Scalafix {
    val organizeImports = ivy"com.github.liancheng::organize-imports:0.4.0"
  }

  object Caliban {
    val version = "1.1.0"
    val core    = ivy"com.github.ghostdogpr::caliban:$version"
    val http4s  = ivy"com.github.ghostdogpr::caliban-http4s:$version"
    val cats    = ivy"com.github.ghostdogpr::caliban-cats:$version"
  }

  object Doobie {
    val version = "0.12.1"
    val core    = ivy"org.tpolecat::doobie-core:$version"
  }

  object Http4s {
    val version           = "0.21.22"
    val http4sDsl         = ivy"org.http4s::http4s-dsl:$version"
    val http4sBlazeServer = ivy"org.http4s::http4s-blaze-server:$version"
    val http4sBlazeClient = ivy"org.http4s::http4s-blaze-client:$version"
  }

  object Circe {
    val version = "0.12.3"
    val core    = ivy"io.circe::circe-core:$version"
    val generic = ivy"io.circe::circe-generic:$version"
    val parser  = ivy"io.circe::circe-parser:$version"
  }
}
