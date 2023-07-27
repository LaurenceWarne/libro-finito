// build.sc
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:$MILL_VERSION`
import $ivy.`com.lihaoyi::mill-contrib-docker:$MILL_VERSION`
import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`
import $ivy.`com.goyeau::mill-scalafix_mill0.10:0.2.11`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.10:0.1.4`
import $file.plugins.calibanSchemaGen
import $file.plugins.jmh
import mill._, scalalib._, scalafmt._
import mill.scalalib.publish._
import mill.contrib.buildinfo.BuildInfo
import mill.contrib.docker.DockerModule
import mill.contrib.scoverage.{ScoverageModule, ScoverageReport}
import mill.eval.Evaluator
import com.goyeau.mill.scalafix.ScalafixModule
import coursier.maven.MavenRepository
import calibanSchemaGen.{CalibanClientModule, CalibanSchemaModule}
import jmh.Jmh
import de.tobiasroeser.mill.vcs.version.VcsVersion

val gqlSchemaPath  = "schema.gql"
val finPackageName = "fin"

object finito extends Module {

  object main extends LibroFinitoModule with BuildInfo with DockerModule {

    object docker extends DockerConfig

    object it extends Tests with LibroFinitoTest with CalibanClientModule {

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
        Deps.Caliban.cats,
        Deps.Caliban.core,
        Deps.Caliban.http4s,
        Deps.CaseApp.cats,
        Deps.CaseApp.core,
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
        Deps.Http4s.http4sBlazeClient,
        Deps.Http4s.http4sBlazeServer,
        Deps.Http4s.http4sDsl,
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
        Deps.CatsEffect.catsEffect,
        Deps.Circe.core,
        Deps.Circe.generic
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
        Deps.Http4s.http4sBlazeClient,
        Deps.Http4s.http4sDsl,
        Deps.Http4s.http4sCirce,
        Deps.CatsEffect.catsEffect,
        Deps.CatsLogging.core,
        Deps.luaj
      )

    object test extends Tests with ScoverageTests with LibroFinitoTest {
      def ivyDeps =
        super.ivyDeps() ++ Agg(Deps.CatsLogging.slf4j)
      def moduleDeps = super.moduleDeps ++ Seq(persistence.test)
    }
  }

  object persistence extends LibroFinitoModule {

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

    object test extends Tests with ScoverageTests with LibroFinitoTest {
      def ivyDeps = super.ivyDeps() ++ Agg(Deps.Http4s.client)
    }
  }

  object benchmark extends LibroFinitoModule with Jmh {
    def moduleDeps = Seq(main)

    def ivyDeps = super.ivyDeps() ++ Agg(Deps.jmh)
  }
}
// TODO use this when https://github.com/com-lihaoyi/mill/pull/1309 is merged
// object scoverage extends ScoverageReport {
//   def scalaVersion     = Deps.scalaVersion
//   def scoverageVersion = Deps.scoverageVersion
// }

trait LibroFinitoModuleNoLinting extends ScalaModule with ScoverageModule {
  def scalaVersion    = Deps.scalaVersion
  def ammoniteVersion = Deps.ammoniteVersion
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
  def scalafixIvyDeps = Deps.Scalafix.all
}

trait LibroFinitoTest
    extends TestModule
    with ScalafmtModule
    with ScalafixModule {
  def scalafixIvyDeps = Deps.Scalafix.all
  def scalacOptions   = Options.scalacOptions

  def ivyDeps = Agg(Deps.weaver)
  // https://github.com/disneystreaming/weaver-test
  def testFramework = "weaver.framework.CatsEffect"
}

object Options {
  // From https://gist.github.com/guilgaly/b73ad98384051ecdc7be39b1f053fc87
  val scalacOptions = Seq(
    "-encoding",
    "utf-8",                 // Specify character encoding used by source files.
    "-Ybackend-parallelism", //
    "8",
    "-explaintypes", // Explain type errors in more detail.
    "-feature",      // Emit warning and location for usages of features that should be imported explicitly.
    "-unchecked",    // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit",   // Wrap field accessors to throw an exception on uninitialized access.

    // ********** Warning Settings ***********************************************
    "-Werror",            // Fail the compilation if there are any warnings.
    "-Wdead-code",        // Warn when dead code is identified.
    "-Wextra-implicit",   // Warn when more than one implicit parameter section is defined.
    "-Wmacros:after",     // Only inspect expanded trees when generating unused symbol warnings.
    "-Wnumeric-widen",    // Warn when numerics are widened.
    "-Woctal-literal",    // Warn on obsolete octal syntax.
    "-Wunused:imports",   // Warn if an import selector is not referenced.
    "-Wunused:patvars",   // Warn if a variable bound in a pattern is unused.
    "-Wunused:privates",  // Warn if a private member is unused.
    "-Wunused:locals",    // Warn if a local definition is unused.
    "-Wunused:explicits", // Warn if an explicit parameter is unused.
    "-Wunused:implicits", // Warn if an implicit parameter is unused.
    "-Wunused:params",    // Enable -Wunused:explicits,implicits.
    "-Wvalue-discard",    // Warn when non-Unit expression results are unused.

    // ********** -Xlint: Enable recommended warnings ****************************
    "-Xlint:adapted-args",           // Warn if an argument list is modified to match the receiver.
    "-Xlint:nullary-unit",           // Warn when nullary methods return Unit.
    "-Xlint:inaccessible",           // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any",              // Warn when a type argument is inferred to be Any.
    "-Xlint:doc-detached",           // A Scaladoc comment appears to be detached from its element.
    "-Xlint:private-shadow",         // A private field (or class parameter) shadows a superclass field.
    "-Xlint:type-parameter-shadow",  // A local type parameter shadows a type already in scope.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:option-implicit",        // Option.apply used implicit view.
    "-Xlint:delayedinit-select",     // Selecting member of DelayedInit.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:stars-align",            // Pattern sequence wildcard must align with sequence component.
    "-Xlint:constant",               // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:nonlocal-return",        // A return statement used an exception for flow control.
    "-Xlint:implicit-not-found",     // Check @implicitNotFound and @implicitAmbiguous messages.
    "-Xlint:serial",                 // @SerialVersionUID on traits and non-serializable classes.
    "-Xlint:valpattern",             // Enable pattern checks in val definitions.
    "-Xlint:eta-zero",               // Warn on eta-expansion (rather than auto-application) of zero-ary method.
    "-Xlint:eta-sam",                // Warn on eta-expansion to meet a Java-defined functional interface that is not explicitly annotated with @FunctionalInterface.
    "-Xlint:deprecation"             // Enable linted deprecations.
  )
}

object Deps {
  val scalaVersion     = "2.13.8"
  val ammoniteVersion  = "2.5.2"
  val scoverageVersion = "1.4.11"
  val logback          = ivy"ch.qos.logback:logback-classic:1.1.3"
  val weaver           = ivy"com.disneystreaming::weaver-cats:0.8.1"
  val sqlite           = ivy"org.xerial:sqlite-jdbc:3.41.2.1"
  val flyway           = ivy"org.flywaydb:flyway-core:7.10.0"
  val pureconfig       = ivy"com.github.pureconfig::pureconfig:0.16.0"
  // https://github.com/luaj/luaj/issues/91 ):
  val luaj           = ivy"org.luaj:luaj-jse:3.0.1"
  val testContainers = ivy"com.dimafeng::testcontainers-scala:0.40.2"
  val sttpHttp4s     = ivy"com.softwaremill.sttp.client3::http4s-backend:3.8.8"
  val jmh            = ivy"org.openjdk.jmh:jmh-core:1.35"

  object Compiler {
    val semanticDb       = ivy"org.scalameta::semanticdb-scalac:4.4.22"
    val betterMonadicFor = ivy"com.olegpy::better-monadic-for:0.3.1"
    val kindProjector    = ivy"org.typelevel:::kind-projector:0.13.2"
  }

  object Scalafix {
    private val typelevelVersion = "0.1.5"
    val all = Agg(
      ivy"com.github.liancheng::organize-imports:0.4.0",
      ivy"org.typelevel::typelevel-scalafix:$typelevelVersion",
      ivy"org.typelevel::typelevel-scalafix-cats:$typelevelVersion",
      ivy"org.typelevel::typelevel-scalafix-cats-effect:$typelevelVersion",
      ivy"org.typelevel::typelevel-scalafix-fs2:$typelevelVersion",
      ivy"org.typelevel::typelevel-scalafix-http4s:$typelevelVersion"
    )
  }

  object CatsEffect {
    val version    = "3.4.8"
    val catsEffect = ivy"org.typelevel::cats-effect:$version"
  }

  object Fs2 {
    val version = "3.4.0"
    val core    = ivy"co.fs2::fs2-core:$version"
    val io      = ivy"co.fs2::fs2-io:$version"
  }

  object CatsLogging {
    val version = "2.1.1"
    val core    = ivy"org.typelevel::log4cats-core:$version"
    val slf4j   = ivy"org.typelevel::log4cats-slf4j:$version"
  }

  object Caliban {
    val version = "2.0.2"
    val core    = ivy"com.github.ghostdogpr::caliban:$version"
    val http4s  = ivy"com.github.ghostdogpr::caliban-http4s:$version"
    val cats    = ivy"com.github.ghostdogpr::caliban-cats:$version"
    val client  = ivy"com.github.ghostdogpr::caliban-client:$version"
  }

  object Doobie {
    val version = "1.0.0-RC2"
    val core    = ivy"org.tpolecat::doobie-core:$version"
    val hikari  = ivy"org.tpolecat::doobie-hikari:$version"
  }

  object Http4s {
    val version           = "0.23.13"
    val core              = ivy"org.http4s::http4s-core:$version"
    val client            = ivy"org.http4s::http4s-client:$version"
    val http4sDsl         = ivy"org.http4s::http4s-dsl:$version"
    val http4sBlazeServer = ivy"org.http4s::http4s-blaze-server:$version"
    val http4sBlazeClient = ivy"org.http4s::http4s-blaze-client:$version"
    val http4sCirce       = ivy"org.http4s::http4s-circe:$version"
  }

  object Circe {
    val version = "0.14.1"
    val core    = ivy"io.circe::circe-core:$version"
    val generic = ivy"io.circe::circe-generic:$version"
    val parser  = ivy"io.circe::circe-parser:$version"
    val literal = ivy"io.circe::circe-literal:$version"
  }

  object CaseApp {
    val version = "2.1.0-M6"
    val core    = ivy"com.github.alexarchambault::case-app:$version"
    val cats    = ivy"com.github.alexarchambault::case-app-cats:$version"
  }
}
