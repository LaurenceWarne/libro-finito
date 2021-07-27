package fin.service

import java.util.HashMap
import javax.script.{Bindings, SimpleBindings}

import scala.jdk.CollectionConverters._

import cats.Contravariant
import cats.implicits._
import cats.kernel.Monoid

import fin.Types._

final case class SBindings(bindings: Map[String, Object]) {
  def asJava: Bindings = {
    val javaBindings = new SimpleBindings(new HashMap(bindings.asJava))
    javaBindings
  }
}

object SBindings {

  val empty = SBindings(Map.empty)

  implicit val sBindingsMonoid = new Monoid[SBindings] {
    override def empty: SBindings = SBindings.empty
    override def combine(x: SBindings, y: SBindings): SBindings =
      SBindings(x.bindings ++ y.bindings)
  }
}

trait Bindable[T] {
  def asBindings(b: T): SBindings
}

object Bindable {
  def apply[T](implicit b: Bindable[T]): Bindable[T] = b

  def asBindings[T: Bindable](b: T): SBindings =
    implicitly[Bindable[T]].asBindings(b)

  implicit class BindableOps[T: Bindable](b: T) {
    def asBindings: SBindings = Bindable[T].asBindings(b)
  }

  implicit val bindableContravariant: Contravariant[Bindable] =
    new Contravariant[Bindable] {
      def contramap[A, B](fa: Bindable[A])(f: B => A): Bindable[B] =
        b => fa.asBindings(f(b))
    }

  implicit def mapBindable[T]: Bindable[Map[String, T]] =
    m => SBindings(m.asInstanceOf[Map[String, Object]])
  implicit val collectionBindable: Bindable[Collection] =
    mapBindable.contramap { c =>
      Map("collection" -> c.name, "sort" -> c.preferredSort.toString)
    }
  implicit val bookBindable: Bindable[BookInput] =
    mapBindable.contramap { b =>
      Map("title" -> b.title, "isbn" -> b.isbn, "authors" -> b.authors)
    }
}
