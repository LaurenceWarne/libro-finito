package fin.service.summary

import cats.implicits._
import scala.annotation.tailrec

sealed trait ImageChunk

object ImageChunk {
  def fitsIn(width: Int)(chunk: ImageChunk): Boolean = {
    chunk match {
      case CompositeChunk(w, _) if w > width => false
      case _                                 => true
    }
  }
}

final case class SingularChunk(indentifier: String) extends ImageChunk

final case class CompositeChunk(
    width: Int,
    chunks: List[SingularChunk]
) extends ImageChunk {

  def flatten(at: (Int, Int)): List[(SingularChunk, (Int, Int))] =
    chunks.zip(
      LazyList
        .iterate((0, 0)) {
          case (r, c) => (r + c / width, (c + 1) % width)
        }
        .map(_ |+| at)
    )
}

object YearlySummary {

  val nullChunk = SingularChunk(":null")

  def stitch(images: List[ImageChunk], columns: Int): List[SingularChunk] = {
    val gridStream = LazyList.iterate((0, 0)) {
      case (r, c) => (r + c / columns, (c + 1) % columns)
    }
    stitchRec(gridStream, images, Map.empty, columns)
  }

  @tailrec
  private def stitchRec(
      gridStream: LazyList[(Int, Int)],
      unprocessedChunks: List[ImageChunk],
      chunkMapping: Map[SingularChunk, (Int, Int)],
      columns: Int
  ): List[SingularChunk] = {
    val head #:: tail = gridStream
    val fitInFn       = ImageChunk.fitsIn(columns - (head._1 + 1))(_)
    unprocessedChunks match {
      case (c: SingularChunk) :: chunksTail =>
        stitchRec(tail, chunksTail, chunkMapping + (c -> head), columns)
      case (c: CompositeChunk) :: chunksTail if fitInFn(c) =>
        val subChunks = c.flatten(head)
        val fStream   = tail.filterNot(subChunks.map(_._2).contains(_))
        stitchRec(fStream, chunksTail, chunkMapping ++ subChunks.toMap, columns)
      case CompositeChunk(w, _) :: _ =>
        val (fittingChunk, chunks) =
          findFirstAndRemove(unprocessedChunks, ImageChunk.fitsIn(w), nullChunk)
        stitchRec(gridStream, fittingChunk :: chunks, chunkMapping, columns)
      case Nil => chunkMapping.keys.toList.sortBy(chunkMapping(_))
    }
  }

  private def findFirstAndRemove[A](
      list: List[A],
      pred: A => Boolean,
      fallback: A
  ): (A, List[A]) = {
    val (maybeElt, checkedList) =
      list.foldLeft((Option.empty[A], List.empty[A])) {
        case ((maybeElt, ls), elt) =>
          maybeElt match {
            case Some(_)           => (maybeElt, ls :+ elt)
            case None if pred(elt) => (Some(elt), ls)
            case _                 => (None, ls)
          }
      }
    (maybeElt.getOrElse(fallback), checkedList)
  }
}
