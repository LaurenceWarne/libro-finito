// build.sc
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:$MILL_VERSION`
import $ivy.`com.lihaoyi::mill-contrib-docker:$MILL_VERSION`
import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`
import $ivy.`com.goyeau::mill-scalafix:0.2.4`
import $ivy.`com.goyeau::mill-git:0.2.2`
import $file.plugins.calibanSchemaGen
import mill._, scalalib._, scalafmt._
import mill.scalalib.publish._
import mill.contrib.buildinfo.BuildInfo
import mill.contrib.docker.DockerModule
import mill.contrib.scoverage.{ScoverageModule, ScoverageReport}
import mill.eval.Evaluator
import com.goyeau.mill.git.GitVersionModule
import com.goyeau.mill.scalafix.ScalafixModule
import coursier.maven.MavenRepository
import calibanSchemaGen.{CalibanClientModule, CalibanSchemaModule}

val finitoVersion  = GitVersionModule.version()
val gqlSchemaPath  = "schema.gql"
val finPackageName = "fin"

object finito extends Module {

  object main extends LibroFinitoModule with BuildInfo with DockerModule {

    object docker extends DockerConfig

    object it extends Tests with LibroFinitoTest with CalibanClientModule {

      def schemaPath  = gqlSchemaPath
      def packageName = finPackageName

      def ivyDeps =
        super.ivyDeps() ++ Agg(
          Deps.Caliban.client,
          Deps.sttpHttp4s,
          Deps.testContainers
        )
    }

    def buildInfoPackageName = Some(finPackageName)

    def buildInfoMembers: T[Map[String, String]] =
      T {
        Map(
          "version"      -> finitoVersion(),
          "scalaVersion" -> scalaVersion()
        )
      }

    def moduleDeps = Seq(api, core, persistence)

    def assembly =
      T {
        val newPath = T.ctx.dest / s"finito-${finitoVersion()}.jar"
        os.move(super.assembly().path, newPath)
        PathRef(newPath)
      }

    def scalacPluginIvyDeps =
      super.scalacPluginIvyDeps() ++ Agg(
        Deps.Compiler.betterMonadicFor,
        Deps.Compiler.kindProjector
      )

    def ivyDeps =
      Agg(
        Deps.betterFiles,
        Deps.Caliban.cats,
        Deps.Caliban.core,
        Deps.Caliban.http4s,
        Deps.CaseApp.core,
        Deps.CaseApp.cats,
        Deps.Circe.core,
        Deps.Circe.generic,
        Deps.Circe.parser,
        Deps.Doobie.core,
        Deps.Doobie.hikari,
        Deps.Http4s.http4sBlazeClient,
        Deps.Http4s.http4sBlazeServer,
        Deps.Http4s.http4sDsl,
        Deps.CatsEffect.catsEffect,
        Deps.CatsLogging.slf4j,
        Deps.CatsLogging.core,
        Deps.flyway,
        Deps.logback,
        Deps.pureconfig
      )
  }

  object api extends LibroFinitoModuleNoLinting with CalibanSchemaModule {

    def schemaPath         = gqlSchemaPath
    def packageName        = finPackageName
    def abstractEffectType = true
    def scalarMappings     = Map("Date" -> "java.time.LocalDate")

    def ivyDeps =
      Agg(
        Deps.Caliban.core,
        Deps.Caliban.cats,
        Deps.CatsEffect.catsEffect
      )
  }

  object core extends LibroFinitoModule {

    def moduleDeps = Seq(api, persistence)

    def ivyDeps =
      super.ivyDeps() ++ Agg(
        Deps.Caliban.cats,
        Deps.Caliban.core,
        Deps.Caliban.http4s,
        Deps.Circe.core,
        Deps.Circe.generic,
        Deps.Circe.parser,
        Deps.enumeratum,
        Deps.Http4s.http4sBlazeClient,
        Deps.Http4s.http4sDsl,
        Deps.CatsEffect.catsEffect,
        Deps.CatsLogging.core,
        Deps.luaj
      )

    object test extends Tests with ScoverageTests with LibroFinitoTest {
      def ivyDeps = super.ivyDeps() ++ Agg(Deps.CatsLogging.slf4j)
    }
  }

  object persistence extends LibroFinitoModule {

    def moduleDeps = Seq(api)

    def ivyDeps =
      super.ivyDeps() ++ Agg(
        Deps.betterFiles,
        Deps.CatsEffect.catsEffect,
        Deps.CatsLogging.core,
        Deps.Circe.core,
        Deps.Circe.generic,
        Deps.Circe.parser,
        Deps.Doobie.core,
        Deps.Doobie.hikari,
        Deps.flyway,
        Deps.sqlite
      )

    object test extends Tests with ScoverageTests with LibroFinitoTest
  }
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
  val scalacOptions = Seq(
    "-deprecation",
    "-Ywarn-unused",
    "-Xfatal-warnings"
  )
}

object Deps {
  val scalaVersion     = "2.13.6"
  val scoverageVersion = "1.4.8"
  val logback          = ivy"ch.qos.logback:logback-classic:1.1.3"
  val weaver           = ivy"com.disneystreaming::weaver-cats:0.7.4"
  val sqlite           = ivy"org.xerial:sqlite-jdbc:3.34.0"
  val flyway           = ivy"org.flywaydb:flyway-core:7.10.0"
  val pureconfig       = ivy"com.github.pureconfig::pureconfig:0.16.0"
  val betterFiles      = ivy"com.github.pathikrit::better-files:3.9.1"
  val enumeratum       = ivy"com.beachape::enumeratum:1.7.0"
  // https://github.com/luaj/luaj/issues/91 ):
  val luaj           = ivy"org.luaj:luaj-jse:3.0.1"
  val testContainers = ivy"com.dimafeng::testcontainers-scala:0.39.7"
  val sttpHttp4s     = ivy"com.softwaremill.sttp.client3::http4s-backend:3.3.14"

  object Compiler {
    val semanticDb       = ivy"org.scalameta::semanticdb-scalac:4.4.22"
    val betterMonadicFor = ivy"com.olegpy::better-monadic-for:0.3.1"
    val kindProjector    = ivy"org.typelevel:::kind-projector:0.13.0"
  }

  object Scalafix {
    val organizeImports = ivy"com.github.liancheng::organize-imports:0.4.0"
  }

  object CatsEffect {
    val version    = "3.2.5"
    val catsEffect = ivy"org.typelevel::cats-effect:$version"
  }

  object CatsLogging {
    val version = "2.1.1"
    val core    = ivy"org.typelevel::log4cats-core:$version"
    val slf4j   = ivy"org.typelevel::log4cats-slf4j:$version"
  }

  object Caliban {
    val version = "1.1.1"
    val core    = ivy"com.github.ghostdogpr::caliban:$version"
    val http4s  = ivy"com.github.ghostdogpr::caliban-http4s:$version"
    val cats    = ivy"com.github.ghostdogpr::caliban-cats:$version"
    val client  = ivy"com.github.ghostdogpr::caliban-client:$version"
  }

  object Doobie {
    val version = "1.0.0-RC1"
    val core    = ivy"org.tpolecat::doobie-core:$version"
    val hikari  = ivy"org.tpolecat::doobie-hikari:$version"
  }

  object Http4s {
    val version           = "0.23.1"
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

  object CaseApp {
    val version = "2.1.0-M6"
    val core    = ivy"com.github.alexarchambault::case-app:$version"
    val cats    = ivy"com.github.alexarchambault::case-app-cats:$version"
  }
}
