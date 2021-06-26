import $ivy.`com.github.ghostdogpr::caliban-tools:1.0.1`
import caliban.tools.Codegen.GenType
import caliban.tools._
import mill._
import mill.eval.Evaluator
import zio.Runtime

object SchemaGen {

  def gen(
      ev: Evaluator,
      schemaPath: String,
      toPath: String,
      fmtPath: Option[String] = None,
      headers: Option[List[Options.Header]] = None,
      packageName: Option[String] = None,
      genView: Option[Boolean] = None,
      effect: Option[String] = Some("IO"),
      scalarMappings: Option[Map[String, String]] = Some(Map("ID" -> "UUID")),
      imports: Option[List[String]] = Some(
        List("java.util.UUID", "cats.effect.IO")
      )
  ) =
    T.command {
      val options = Options(
        schemaPath,
        toPath,
        fmtPath,
        headers,
        packageName,
        genView,
        effect,
        scalarMappings,
        imports
      )
      println("Options: " + options.toString)
      Runtime.default.unsafeRun(
        Codegen.generate(options, GenType.Schema).unit
      )
    }
}
