import $ivy.`com.github.ghostdogpr::caliban-tools:2.4.3`
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
  def clientName: String            = "Client"
  def packageName: String
  def genView: Boolean                    = false
  def effect: String                      = if (abstractEffectType) "F" else "zio.UIO"
  def scalarMappings: Map[String, String] = Map.empty
  def imports: List[String]               = List.empty
  def splitFiles: Boolean                 = false
  def enableFmt: Boolean                  = false
  def extensibleEnums: Boolean            = false
  def abstractEffectType: Boolean         = false
  def preserveInputNames: Boolean         = true
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
        Some(clientName),
        Some(genView),
        Some(effect),
        Some(scalarMappings),
        Some(imports),
        Some(abstractEffectType),
        Some(splitFiles),
        Some(enableFmt),
        Some(extensibleEnums),
        Some(preserveInputNames),
        None,
        None
      )
      zio.Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe
          .run(Codegen.generate(options, GenType.Schema).unit)
          .getOrThrowFiberFailure()
      }
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
        Some(clientName),
        Some(genView),
        Some(effect),
        Some(scalarMappings),
        Some(imports),
        Some(abstractEffectType),
        Some(splitFiles),
        Some(enableFmt),
        Some(extensibleEnums),
        Some(preserveInputNames),
        None,
        None
      )
      zio.Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe
          .run(Codegen.generate(options, GenType.Client).unit)
          .getOrThrowFiberFailure()
      }
      PathRef(outputPath)
    }
}
