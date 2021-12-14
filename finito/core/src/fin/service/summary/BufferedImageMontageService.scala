package fin.service.summary

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

import cats.Parallel
import cats.effect.kernel.Async
import cats.implicits._
import org.typelevel.log4cats.Logger

import fin.NoBooksFoundForMontageError
import fin.Types._

class BufferedImageMontageService[F[_]: Async: Parallel: Logger]
    extends MontageService[F] {

  private val imageType = BufferedImage.TYPE_INT_ARGB

  override def montage(
      books: List[UserBook],
      maybeSpecification: Option[MontageInput]
  ): F[String] = {
    val specification = maybeSpecification.getOrElse(MontageInputs.default)
    for {
      chunksEither <- books.parTraverse { b =>
        download(b.thumbnailUri).map { img =>
          val width  = specification.largeImageWidth
          val height = specification.largeImageHeight
          if (b.rating.exists(_ >= specification.largeImageRatingThreshold)) {
            val resizedImg = resize(img, width, height)
            split(resizedImg, specification)
          } else {
            val resizedImg = resize(img, width / 2, height / 2)
            (SingularChunk(resizedImg): ImageChunk)
          }
        }.attempt
      }
      (errors, chunks) = chunksEither.partitionEither(identity)
      _ <- Logger[F].error(errors.toString)
      _ <- Async[F].raiseWhen(chunks.isEmpty)(NoBooksFoundForMontageError)
      map = ImageStitch.stitch(chunks, specification.columns)
      img = collageBufferedImages(map, specification)
      b64 <- imgToBase64(img)
    } yield b64
  }

  private def imgToBase64(img: BufferedImage): F[String] =
    for {
      os <- Async[F].delay(new ByteArrayOutputStream())
      _  <- Async[F].delay(ImageIO.write(img, "png", os))
      b64 <-
        Async[F].delay(Base64.getEncoder().encodeToString(os.toByteArray()))
    } yield b64

  private def collageBufferedImages(
      chunkMapping: Map[(Int, Int), SingularChunk],
      specification: MontageInput
  ): BufferedImage = {
    val columns = specification.columns
    val (w, h)  = MontageInputs.smallImageDim(specification)
    val rows    = (chunkMapping.keySet.map(_._1) + 0).max
    val img     = new BufferedImage(columns * w, (rows + 1) * h, imageType)
    val g2d     = img.createGraphics()
    chunkMapping.foreach {
      case ((r, c), chunk) => g2d.drawImage(chunk.img, c * w, r * h, null)
    }
    img
  }

  private def download(uri: String): F[BufferedImage] =
    for {
      url <- Async[F].delay(new java.net.URL(uri))
      img <- Async[F].blocking(ImageIO.read(url))
    } yield img

  private def resize(img: BufferedImage, w: Int, h: Int): BufferedImage = {
    // https://stackoverflow.com/questions/9417356/bufferedimage-resize
    val tmp  = img.getScaledInstance(w, h, Image.SCALE_SMOOTH)
    val dimg = new BufferedImage(w, h, imageType)
    val g2d  = dimg.createGraphics()
    g2d.drawImage(tmp, 0, 0, null)
    g2d.dispose()
    dimg
  }

  private def split(
      img: BufferedImage,
      specification: MontageInput
  ): CompositeChunk = {
    val imgScaleFactor = specification.largeImgScaleFactor
    val (w, h)         = MontageInputs.smallImageDim(specification)
    val subImages = List
      .tabulate(imgScaleFactor, imgScaleFactor) {
        case (y, x) => SingularChunk(img.getSubimage(x * w, y * h, w, h))
      }
      .flatten
    CompositeChunk(imgScaleFactor, subImages.toList)
  }
}

object BufferedImageMontageService {
  def apply[F[_]: Async: Parallel: Logger] = new BufferedImageMontageService[F]
}

object MontageInputs {
  def default: MontageInput = new MontageInput(6, 128, 196, 2, 5)
  def smallImageDim(specification: MontageInput): (Int, Int) =
    (smallImageWidth(specification), smallImageHeight(specification))
  def smallImageWidth(specification: MontageInput): Int =
    specification.largeImageWidth / specification.largeImgScaleFactor
  def smallImageHeight(specification: MontageInput): Int =
    specification.largeImageHeight / specification.largeImgScaleFactor
}
