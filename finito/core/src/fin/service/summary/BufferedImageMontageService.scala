package fin.service.summary

import cats.implicits._

import fin.Types._
import org.http4s.client.Client
import cats.Parallel
import fs2.io.file._
import cats.effect.kernel.Async
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.awt.Image

class BufferedImageMontageService[F[_]: Async: Parallel](
    client: Client[F],
    specification: MontageSpecification,
    tmpDirectory: String = "/tmp"
) extends MontageService[F] {

  override def montage(books: List[UserBook]): F[String] =
    for {
      chunks <- books.parTraverse { b =>
        download(b.thumbnailUri, b.title).map { img =>
          val resizedImg = resize(img)
          if (specification.largeImgPredicate(b)) split(resizedImg)
          else (SingularChunk(resizedImg): ImageChunk)
        }
      }
      map = ImageStitch.stitch(chunks, specification.columns)
      img = collageBufferedImages(map)
    } yield img.toString

  private def collageBufferedImages(
      chunkMapping: Map[(Int, Int), SingularChunk]
  ): BufferedImage = {
    val MontageSpecification(columns, width, height, largeImgScalaFactor, _) =
      specification
    val rows = chunkMapping.keySet.map(_._1).max
    val img =
      new BufferedImage(
        columns * (width / largeImgScalaFactor),
        rows * (height / largeImgScalaFactor),
        BufferedImage.TYPE_INT_ARGB
      )
    val g2d = img.createGraphics()
    chunkMapping.foreach {
      case ((r, c), chunk) =>
        g2d.drawImage(chunk.img, c * width, r * height, null)
    }
    img
  }

  private def download(uri: String, fileName: String): F[BufferedImage] = {
    val path = Path(tmpDirectory) / (fileName + ".jpg")
    for {
      _ <-
        client
          .get(uri)(
            _.body.bufferAll
              .through(Files[F].writeAll(path, Flags.Write))
              .compile
              .drain
          )
      img <- Async[F].delay(ImageIO.read(path.toNioPath.toFile))
    } yield img
  }

  private def resize(img: BufferedImage): BufferedImage = {
    val MontageSpecification(_, width, height, _, _) = specification
    // https://stackoverflow.com/questions/9417356/bufferedimage-resize
    val tmp  = img.getScaledInstance(width, height, Image.SCALE_SMOOTH)
    val dimg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2d  = dimg.createGraphics()
    g2d.drawImage(tmp, 0, 0, null)
    g2d.dispose()
    dimg
  }

  private def split(img: BufferedImage): ImageChunk = {
    val MontageSpecification(columns, width, height, largeImgScalaFactor, _) =
      specification
    val (w, h) = (width / largeImgScalaFactor, height / largeImgScalaFactor)
    val subImages = LazyList
      .iterate((0, 0)) {
        case (x, y) => ((x + w) % (w * columns), y + h * (x / (w * columns)))
      }
      .map { case (x, y) => SingularChunk(img.getSubimage(x, y, w, h)) }
    CompositeChunk(columns, subImages.toList)
  }
}

final case class MontageSpecification(
    columns: Int = 6,
    /** Corresponds the width of large images */
    imageWidth: Int = 128,
    /** Corresponds the width of large images */
    imageHeight: Int = 196,
    largeImgScaleFactor: Int = 2,
    largeImgPredicate: UserBook => Boolean = b => b.rating.exists(_ >= 5)
)
