package fin.service

trait DefaultCollectionService[F[_]] {
  def defaultCollectionName: String
  def createDefaultCollection: F[Unit]
}
