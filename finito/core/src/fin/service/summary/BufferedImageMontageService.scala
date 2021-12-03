package fin.service.summary

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

import cats.Parallel
import cats.effect.kernel.Async
import cats.implicits._
import org.http4s.client.Client

import fin.Types._

class BufferedImageMontageService[F[_]: Async: Parallel] private (
    client: Client[F],
    specification: MontageSpecification
) extends MontageService[F] {

  private val imageType = BufferedImage.TYPE_INT_ARGB

  override def montage(books: List[UserBook]): F[String] =
    for {
      chunks <- books.parTraverse { b =>
        download(b.thumbnailUri).map { img =>
          val MontageSpecification(_, width, height, _, _) = specification
          if (specification.largeImgPredicate(b)) {
            val resizedImg = resize(img, width, height)
            split(resizedImg)
          } else {
            val resizedImg = resize(img, width / 2, height / 2)
            (SingularChunk(resizedImg): ImageChunk)
          }
        }
      }
      map = ImageStitch.stitch(chunks, specification.columns)
      img = collageBufferedImages(map)
      _ <- Async[F].delay(ImageIO.write(img, "png", new File("montage.png")))
    } yield map.toString

  private def collageBufferedImages(
      chunkMapping: Map[(Int, Int), SingularChunk]
  ): BufferedImage = {
    val MontageSpecification(columns, widthL, heightL, largeImgScalaFactor, _) =
      specification
    val (w, h) = (widthL / largeImgScalaFactor, heightL / largeImgScalaFactor)
    val rows   = (chunkMapping.keySet.map(_._1) + 0).max
    val img    = new BufferedImage(columns * w, (rows + 1) * h, imageType)
    val g2d    = img.createGraphics()
    chunkMapping.foreach {
      case ((r, c), chunk) => g2d.drawImage(chunk.img, c * w, r * h, null)
    }
    img
  }

  private def download(uri: String): F[BufferedImage] = {
    for {
      img <- client.get(uri)(
        _.body.bufferAll
          .through(fs2.io.toInputStream)
          .map(ImageIO.read)
          .compile
          .lastOrError
      )
    } yield img
  }

  private def resize(img: BufferedImage, w: Int, h: Int): BufferedImage = {
    // https://stackoverflow.com/questions/9417356/bufferedimage-resize
    val tmp  = img.getScaledInstance(w, h, Image.SCALE_SMOOTH)
    val dimg = new BufferedImage(w, h, imageType)
    val g2d  = dimg.createGraphics()
    g2d.drawImage(tmp, 0, 0, null)
    g2d.dispose()
    dimg
  }

  private def split(img: BufferedImage): CompositeChunk = {
    val MontageSpecification(_, width, height, largeImgScaleFactor, _) =
      specification
    val (w, h) = (width / largeImgScaleFactor, height / largeImgScaleFactor)
    val subImages = List
      .tabulate(largeImgScaleFactor, largeImgScaleFactor) {
        case (y, x) => SingularChunk(img.getSubimage(x * w, y * h, w, h))
      }
      .flatten
    CompositeChunk(largeImgScaleFactor, subImages.toList)
  }
}

object BufferedImageMontageService {
  def apply[F[_]: Async: Parallel](
      client: Client[F],
      specification: MontageSpecification
  ) =
    new BufferedImageMontageService[F](
      client,
      specification
    )
}

final case class MontageSpecification(
    columns: Int = 6,
    /** Corresponds the width of large images */
    imageWidth: Int = 128,
    /** Corresponds the height of large images */
    imageHeight: Int = 196,
    largeImgScaleFactor: Int = 2,
    largeImgPredicate: UserBook => Boolean = b => b.rating.exists(_ >= 5)
)
