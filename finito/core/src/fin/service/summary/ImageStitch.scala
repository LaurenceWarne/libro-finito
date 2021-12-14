package fin.service.summary

import java.awt.image.BufferedImage

import scala.annotation.tailrec

import cats.implicits._

object ImageStitch {

  def stitch(
      images: List[ImageChunk],
      columns: Int
  ): Map[(Int, Int), SingularChunk] = {
    val gridStream = LazyList.iterate((0, 0)) {
      case (r, c) => (r + (c + 1) / columns, (c + 1) % columns)
    }
    stitchRec(gridStream, images, Map.empty, columns)
  }

  @tailrec
  private def stitchRec(
      gridStream: LazyList[(Int, Int)],
      unprocessedChunks: List[ImageChunk],
      chunkMapping: Map[(Int, Int), SingularChunk],
      columns: Int
  ): Map[(Int, Int), SingularChunk] = {
    val head #:: tail = gridStream
    val fitInFn =
      ImageChunk.fitsAt(head, columns - head._2, chunkMapping.keySet)(_)
    unprocessedChunks match {
      case (c: SingularChunk) :: chunksTail =>
        stitchRec(tail, chunksTail, chunkMapping + (head -> c), columns)
      case (c: CompositeChunk) :: chunksTail if fitInFn(c) =>
        val subChunks = c.flatten(head)
        val fStream   = tail.filterNot(subChunks.map(_._1).contains(_))
        stitchRec(fStream, chunksTail, chunkMapping ++ subChunks.toMap, columns)
      case (_: CompositeChunk) :: _ =>
        val (maybeMatch, chunks) =
          findFirstAndRemove(unprocessedChunks, fitInFn)
        stitchRec(
          if (maybeMatch.isEmpty) gridStream.init else gridStream,
          chunks.prependedAll(maybeMatch),
          chunkMapping,
          columns
        )
      case Nil => chunkMapping
    }
  }

  private def findFirstAndRemove[A](
      list: List[A],
      pred: A => Boolean
  ): (Option[A], List[A]) = {
    val (maybeElt, checkedList) =
      list.foldLeft((Option.empty[A], List.empty[A])) {
        case ((maybeElt, ls), elt) =>
          maybeElt match {
            case Some(_)           => (maybeElt, ls :+ elt)
            case None if pred(elt) => (Some(elt), ls)
            case _                 => (None, ls)
          }
      }
    (maybeElt, checkedList)
  }
}

sealed trait ImageChunk extends Product with Serializable

object ImageChunk {
  def fitsAt(rowColumn: (Int, Int), width: Int, filled: Set[(Int, Int)])(
      chunk: ImageChunk
  ): Boolean = {
    chunk match {
      case c @ CompositeChunk(w, _)
          if (w > width || c.flatten(rowColumn).exists(c => filled(c._1))) =>
        false
      case _ => true
    }
  }
}

final case class SingularChunk(img: BufferedImage) extends ImageChunk

final case class CompositeChunk(
    width: Int,
    chunks: List[SingularChunk]
) extends ImageChunk {

  def flatten(at: (Int, Int)): List[((Int, Int), SingularChunk)] =
    LazyList
      .iterate((0, 0)) {
        case (r, c) => (r + (c + 1) / width, (c + 1) % width)
      }
      .map(_ |+| at)
      .zip(chunks)
      .toList
}
