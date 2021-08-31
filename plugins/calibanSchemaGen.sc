import $ivy.`com.github.ghostdogpr::caliban-tools:1.1.0`
import caliban.tools.Codegen.GenType
import caliban.tools._
import mill._, scalalib._, scalafmt._
import mill.api.PathRef
import zio.Runtime

trait CalibanModule {

  def schemaPath: String
  def outputFileName                = "Schema.scala"
  def fmtPath: String               = ".scalafmt.conf"
  def headers: List[Options.Header] = List.empty
  def packageName: String
  def genView: Boolean                    = false
  def effect: String                      = if (abstractEffectType) "F" else "zio.UIO"
  def scalarMappings: Map[String, String] = Map.empty
  def imports: List[String]               = List.empty
  def abstractEffectType: Boolean         = false
}

trait CalibanSchemaModule extends ScalaModule with CalibanModule {

  override def generatedSources =
    T {
      val schemaPathRef = schema()
      super.generatedSources() :+ schemaPathRef
    }

  def schema: T[PathRef] =
    T {
      val outputPath = T.dest / outputFileName
      val options = Options(
        schemaPath,
        outputPath.toString,
        Some(fmtPath),
        Some(headers),
        Some(packageName),
        Some(genView),
        Some(effect),
        Some(scalarMappings),
        Some(imports),
        Some(abstractEffectType)
      )
      Runtime.default.unsafeRun(
        Codegen.generate(options, GenType.Schema).unit
      )
      PathRef(outputPath)
    }
}

trait CalibanClientModule extends ScalaModule with CalibanModule {

  override def generatedSources =
    T {
      val schemaPathRef = schema()
      super.generatedSources() :+ schemaPathRef
    }

  def schema: T[PathRef] =
    T {
      val outputPath = T.dest / outputFileName
      val options = Options(
        schemaPath,
        outputPath.toString,
        Some(fmtPath),
        Some(headers),
        Some(packageName),
        Some(genView),
        Some(effect),
        Some(scalarMappings),
        Some(imports),
        Some(abstractEffectType)
      )
      Runtime.default.unsafeRun(
        Codegen.generate(options, GenType.Client).unit
      )
      PathRef(outputPath)
    }
}
