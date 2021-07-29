package fin

trait FinitoError extends Throwable {
  def errorCode: String
}
