// build.sc
import mill._, scalalib._, scalafmt._

trait LibroFinitoTest extends TestModule {
  def ivyDeps =
    Agg(
      ivy"com.disneystreaming::weaver-framework:0.4.3"
    )
  // https://github.com/disneystreaming/weaver-test
  def testFrameworks = Seq("weaver.framework.TestFramework")
}

object api extends ScalaModule with ScalafmtModule {
  def scalaVersion = Deps.scalaVersion

  def moduleDeps = Seq(core)

  def scalacPluginIvyDeps = Agg(Deps.betterMonadicFor)
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

object core extends ScalaModule with ScalafmtModule {
  def scalaVersion = Deps.scalaVersion
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

object Deps {
  val scalaVersion = "2.13.5"
  val catsEffect = ivy"org.typelevel::cats-effect:2.5.0"
  val catsLogging = ivy"io.chrisdavenport::log4cats-slf4j:1.1.1"
  val betterMonadicFor = ivy"com.olegpy::better-monadic-for:0.3.1"

  object Caliban {
    val version = "0.9.5"
    val core = ivy"com.github.ghostdogpr::caliban:$version"
    val http4s = ivy"com.github.ghostdogpr::caliban-http4s:$version"
    val cats = ivy"com.github.ghostdogpr::caliban-cats:$version"
  }

  object Http4s {
    val version = "0.21.22"
    val http4sDsl = ivy"org.http4s::http4s-dsl:$version"
    val http4sBlazeServer = ivy"org.http4s::http4s-blaze-server:$version"
    val http4sBlazeClient = ivy"org.http4s::http4s-blaze-client:$version"
  }

  object Circe {
    val version = "0.12.3"
    val core = ivy"io.circe::circe-core:$version"
    val generic = ivy"io.circe::circe-generic:$version"
    val parser = ivy"io.circe::circe-parser:$version"
  }
}
