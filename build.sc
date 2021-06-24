// build.sc
import mill._, scalalib._, scalafmt._
import mill.eval.Evaluator
import $ivy.`com.goyeau::mill-scalafix:0.2.4`
import $file.plugins.calibanSchemaGen
import com.goyeau.mill.scalafix.ScalafixModule
import calibanSchemaGen.SchemaGen

def genSchema(ev: Evaluator, schemaPath: String, toPath: String) =
  T.command {
    SchemaGen.gen(ev, schemaPath, toPath)
  }

trait LibroFinitoModule
    extends ScalaModule
    with ScalafmtModule
    with ScalafixModule {
  def scalaVersion    = Deps.scalaVersion
  def scalafixIvyDeps = Agg(Deps.Scalafix.organizeImports)
  def scalacOptions   = Options.scalacOptions
}

trait LibroFinitoTest
    extends TestModule
    with ScalafmtModule
    with ScalafixModule {
  def scalafixIvyDeps = Agg(Deps.Scalafix.organizeImports)
  def scalacOptions   = Options.scalacOptions

  def ivyDeps =
    Agg(
      ivy"com.disneystreaming::weaver-framework:0.4.3"
    )
  // https://github.com/disneystreaming/weaver-test
  def testFramework = "weaver.framework.TestFramework"
}

object api extends LibroFinitoModule {

  def moduleDeps = Seq(core)
  //def forkArgs   = Seq("-Xmx100m")

  def scalacPluginIvyDeps =
    super.scalacPluginIvyDeps() ++ Agg(Deps.Compiler.betterMonadicFor)
  def ivyDeps =
    Agg(
      Deps.catsEffect,
      Deps.catsLogging,
      Deps.Caliban.core,
      Deps.Caliban.http4s,
      Deps.Caliban.cats,
      Deps.Http4s.http4sDsl,
      Deps.Http4s.http4sBlazeClient,
      Deps.Http4s.http4sBlazeServer,
      Deps.Circe.core,
      Deps.Circe.generic,
      Deps.Circe.parser
    )
}

object core extends LibroFinitoModule {

  def ivyDeps =
    Agg(
      Deps.catsEffect,
      Deps.catsLogging,
      Deps.Caliban.core,
      Deps.Caliban.http4s,
      Deps.Caliban.cats,
      Deps.Http4s.http4sDsl,
      Deps.Http4s.http4sBlazeClient,
      Deps.Http4s.http4sBlazeServer,
      Deps.Circe.core,
      Deps.Circe.generic,
      Deps.Circe.parser
    )

  object test extends Tests with LibroFinitoTest
}

object Options {
  val scalacOptions = Seq("-Ywarn-unused", "-Xfatal-warnings")
}

object Deps {
  val scalaVersion = "2.13.6"
  val catsEffect   = ivy"org.typelevel::cats-effect:2.5.0"
  val catsLogging  = ivy"io.chrisdavenport::log4cats-slf4j:1.1.1"

  object Compiler {
    val semanticDb       = ivy"org.scalameta::semanticdb-scalac:4.4.22"
    val betterMonadicFor = ivy"com.olegpy::better-monadic-for:0.3.1"
  }

  object Scalafix {
    val organizeImports = ivy"com.github.liancheng::organize-imports:0.4.0"
  }

  object Caliban {
    val version = "0.9.5"
    val core    = ivy"com.github.ghostdogpr::caliban:$version"
    val http4s  = ivy"com.github.ghostdogpr::caliban-http4s:$version"
    val cats    = ivy"com.github.ghostdogpr::caliban-cats:$version"
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
