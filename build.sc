// build.sc
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:$MILL_VERSION`
import $ivy.`com.lihaoyi::mill-contrib-docker:$MILL_VERSION`
import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`
import $ivy.`com.lihaoyi::mill-contrib-jmh:$MILL_VERSION`
import $ivy.`com.goyeau::mill-scalafix_mill0.11:0.4.2`
import $ivy.`io.github.davidgregory084::mill-tpolecat::0.3.5`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.11:0.4.0`
import $file.plugins.calibanSchemaGen
import mill._, scalalib._, scalafmt._
import calibanSchemaGen.{CalibanClientModule, CalibanSchemaModule}
import com.goyeau.mill.scalafix.ScalafixModule
import contrib.jmh.JmhModule
import coursier.maven.MavenRepository
import de.tobiasroeser.mill.vcs.version.VcsVersion
import io.github.davidgregory084.TpolecatModule
import mill.contrib.buildinfo.BuildInfo
import mill.contrib.docker.DockerModule
import mill.contrib.scoverage.{ScoverageModule, ScoverageReport}
import mill.eval.Evaluator
import mill.scalalib.publish._

val gqlSchemaPath  = "schema.gql"
val finPackageName = "fin"

object finito extends Module {

  object main extends LibroFinitoModule with BuildInfo with DockerModule {

    object docker extends DockerConfig

    object it extends LibroFinitoTest with ScalaTests with CalibanClientModule {

      def schemaPath  = gqlSchemaPath
      def packageName = finPackageName
      def genView     = true

      def ivyDeps =
        super.ivyDeps() ++ Agg(
          Deps.Caliban.client,
          Deps.sttpHttp4s,
          Deps.testContainers
        )
    }

    def finitoVersion: T[String] =
      VcsVersion.vcsState().format(tagModifier = tag => tag.stripPrefix("v"))

    def buildInfoPackageName = finPackageName

    def buildInfoMembers: T[Seq[BuildInfo.Value]] =
      T {
        Seq(
          BuildInfo.Value("version", finitoVersion()),
          BuildInfo.Value("scalaVersion", scalaVersion())
        )
      }

    def moduleDeps = Seq(api, core, persistence)

    def assembly =
      T {
        val newPath = T.ctx().dest / s"finito-${finitoVersion()}.jar"
        os.move(super.assembly().path, newPath)
        PathRef(newPath)
      }

    def ivyDeps =
      super.ivyDeps() ++
        Agg(
          Deps.Caliban.cats,
          Deps.Caliban.core,
          Deps.Caliban.http4s,
          Deps.CatsEffect.catsEffect,
          Deps.CatsLogging.core,
          Deps.CatsLogging.slf4j,
          Deps.Circe.core,
          Deps.Circe.generic,
          Deps.Circe.parser,
          Deps.Circe.literal,
          Deps.Doobie.core,
          Deps.Doobie.hikari,
          Deps.Fs2.core,
          Deps.Fs2.io,
          Deps.Http4s.http4sEmberClient,
          Deps.Http4s.http4sEmberServer,
          Deps.Http4s.http4sDsl,
          Deps.flyway,
          Deps.logback,
          Deps.typesafeConfig
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
        Deps.CatsEffect.catsEffect,
        Deps.Circe.core,
        Deps.Circe.generic
      )
  }

  object core extends LibroFinitoModule with ScoverageModule {

    def scoverageVersion = Deps.scoverageVersion

    def moduleDeps = Seq(api, persistence)

    def ivyDeps =
      super.ivyDeps() ++ Agg(
        Deps.Caliban.cats,
        Deps.Caliban.core,
        Deps.Caliban.http4s,
        Deps.Circe.core,
        Deps.Circe.generic,
        Deps.Circe.genericExtras,
        Deps.Circe.parser,
        Deps.Fs2.csv,
        Deps.Fs2.csvGeneric,
        Deps.Http4s.http4sEmberClient,
        Deps.Http4s.http4sDsl,
        Deps.Http4s.http4sCirce,
        Deps.CatsEffect.catsEffect,
        Deps.CatsLogging.core,
        Deps.luaj
      )

    object test extends ScoverageTests with LibroFinitoTest with ScalaTests {
      def ivyDeps =
        super.ivyDeps() ++ Agg(Deps.CatsLogging.slf4j)
      def moduleDeps = super.moduleDeps ++ Seq(persistence.test)
    }
  }

  object persistence extends LibroFinitoModule with ScoverageModule {

    def scoverageVersion = Deps.scoverageVersion

    def moduleDeps = Seq(api)

    def ivyDeps =
      super.ivyDeps() ++ Agg(
        Deps.CatsEffect.catsEffect,
        Deps.CatsLogging.core,
        Deps.Circe.core,
        Deps.Circe.generic,
        Deps.Circe.parser,
        Deps.Doobie.core,
        Deps.Doobie.hikari,
        Deps.Fs2.core,
        Deps.Fs2.io,
        Deps.flyway,
        Deps.sqlite
      )

    object test extends ScoverageTests with LibroFinitoTest with ScalaTests {
      def ivyDeps = super.ivyDeps() ++ Agg(Deps.Http4s.client)
    }
  }

  object benchmark extends LibroFinitoModule with JmhModule {
    def jmhCoreVersion = "1.35"

    def moduleDeps = Seq(main)
  }
}

object scoverage extends ScoverageReport {
  def scalaVersion = Deps.scalaVersion
  // https://github.com/com-lihaoyi/mill/blob/main/docs/antora/modules/ROOT/pages/Contrib_Modules.adoc#scoverage
  def scoverageVersion = Deps.scoverageVersion
}

trait LibroFinitoModuleNoLinting extends ScalaModule with TpolecatModule {
  def scalaVersion    = Deps.scalaVersion
  def ammoniteVersion = Deps.ammoniteVersion

  def repositoriesTask =
    T.task {
      super.repositoriesTask() ++ Seq(
        MavenRepository("https://jitpack.io")
      )
    }
}

trait LibroFinitoModule
    extends LibroFinitoModuleNoLinting
    with ScalafmtModule
    with ScalafixModule {
  def scalafixIvyDeps = Deps.Scalafix.all

  def scalacPluginIvyDeps =
    super.scalacPluginIvyDeps() ++ Agg(
      Deps.Compiler.betterMonadicFor,
      Deps.Compiler.kindProjector,
      Deps.Compiler.semanticDb
    )
}

trait LibroFinitoTest
    extends ScalaModule
    with TestModule
    with ScalafmtModule
    with ScalafixModule {
  def scalafixIvyDeps = Deps.Scalafix.all

  def ivyDeps = Agg(Deps.weaver)
  // https://github.com/disneystreaming/weaver-test
  def testFramework = "weaver.framework.CatsEffect"
}

object Deps {
  val scalaVersion     = "2.13.15"
  val ammoniteVersion  = "2.5.2"
  val scoverageVersion = "2.2.1"
  val logback          = ivy"ch.qos.logback:logback-classic:1.1.3"
  val weaver           = ivy"com.disneystreaming::weaver-cats:0.8.3"
  val sqlite           = ivy"org.xerial:sqlite-jdbc:3.46.1.3"
  val flyway           = ivy"org.flywaydb:flyway-core:7.10.0"
  // https://github.com/luaj/luaj/issues/91 ):
  val luaj           = ivy"org.luaj:luaj-jse:3.0.1"
  val testContainers = ivy"com.dimafeng::testcontainers-scala:0.40.2"
  val sttpHttp4s     = ivy"com.softwaremill.sttp.client3::http4s-backend:3.10.0"
  val jmh            = ivy"org.openjdk.jmh:jmh-core:1.35"
  // Hard to remove this dep without dropping HOCON, lets hope Lightbend don't start charging for it
  val typesafeConfig = ivy"com.typesafe:config:1.4.3"

  object Compiler {
    val semanticDb       = ivy"org.scalameta:::semanticdb-scalac:4.10.1"
    val betterMonadicFor = ivy"com.olegpy::better-monadic-for:0.3.1"
    val kindProjector    = ivy"org.typelevel:::kind-projector:0.13.3"
  }

  object Scalafix {
    private val typelevelVersion = "0.3.1"
    val all = Agg(
      ivy"org.typelevel::typelevel-scalafix:$typelevelVersion",
      ivy"org.typelevel::typelevel-scalafix-cats:$typelevelVersion",
      ivy"org.typelevel::typelevel-scalafix-cats-effect:$typelevelVersion",
      ivy"org.typelevel::typelevel-scalafix-fs2:$typelevelVersion",
      ivy"org.typelevel::typelevel-scalafix-http4s:$typelevelVersion"
    )
  }

  object CatsEffect {
    val version    = "3.5.4"
    val catsEffect = ivy"org.typelevel::cats-effect:$version"
  }

  object Fs2 {
    val version    = "3.11.0"
    val core       = ivy"co.fs2::fs2-core:$version"
    val io         = ivy"co.fs2::fs2-io:$version"
    val csvVersion = "1.11.1"
    val csv        = ivy"org.gnieh::fs2-data-csv:$csvVersion"
    val csvGeneric = ivy"org.gnieh::fs2-data-csv-generic:$csvVersion"
  }

  object CatsLogging {
    val version = "2.7.0"
    val core    = ivy"org.typelevel::log4cats-core:$version"
    val slf4j   = ivy"org.typelevel::log4cats-slf4j:$version"
  }

  object Caliban {
    val version = "2.9.0"
    val core    = ivy"com.github.ghostdogpr::caliban:$version"
    val http4s  = ivy"com.github.ghostdogpr::caliban-http4s:$version"
    val cats    = ivy"com.github.ghostdogpr::caliban-cats:$version"
    val client  = ivy"com.github.ghostdogpr::caliban-client:$version"
  }

  object Doobie {
    val version = "1.0.0-RC6"
    val core    = ivy"org.tpolecat::doobie-core:$version"
    val hikari  = ivy"org.tpolecat::doobie-hikari:$version"
  }

  object Http4s {
    val version           = "0.23.28"
    val core              = ivy"org.http4s::http4s-core:$version"
    val client            = ivy"org.http4s::http4s-client:$version"
    val http4sDsl         = ivy"org.http4s::http4s-dsl:$version"
    val http4sEmberClient = ivy"org.http4s::http4s-ember-client:$version"
    val http4sEmberServer = ivy"org.http4s::http4s-ember-server:$version"
    val http4sCirce       = ivy"org.http4s::http4s-circe:$version"
  }

  object Circe {
    val version              = "0.14.10"
    val core                 = ivy"io.circe::circe-core:$version"
    val generic              = ivy"io.circe::circe-generic:$version"
    val parser               = ivy"io.circe::circe-parser:$version"
    val literal              = ivy"io.circe::circe-literal:$version"
    val genericExtrasVersion = "0.14.4"
    val genericExtras =
      ivy"io.circe::circe-generic-extras:$genericExtrasVersion"
  }
}
