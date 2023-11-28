package fin.service.collection

import java.util.HashMap
import javax.script.{Bindings, SimpleBindings}

import scala.jdk.CollectionConverters._

import cats.kernel.Monoid
import cats.syntax.all._
import cats.{Contravariant, Show}

import fin.Types._

final case class SBindings(bindings: Map[String, Object]) {
  def asJava: Bindings = {
    val javaBindings = new SimpleBindings(new HashMap(bindings.asJava))
    javaBindings
  }
}

object SBindings {

  val empty = SBindings(Map.empty)

  implicit val sBindingsMonoid: Monoid[SBindings] = new Monoid[SBindings] {
    override def empty: SBindings = SBindings.empty
    override def combine(x: SBindings, y: SBindings): SBindings =
      SBindings(x.bindings ++ y.bindings)
  }
}

trait Bindable[-T] {
  def asBindings(b: T): SBindings
}

object Bindable {
  def apply[T](implicit b: Bindable[T]): Bindable[T] = b

  def asBindings[T: Bindable](b: T): SBindings = Bindable[T].asBindings(b)

  implicit class BindableOps[T: Bindable](b: T) {
    def asBindings: SBindings = Bindable[T].asBindings(b)
  }

  implicit val bindableContravariant: Contravariant[Bindable] =
    new Contravariant[Bindable] {
      def contramap[A, B](fa: Bindable[A])(f: B => A): Bindable[B] =
        b => fa.asBindings(f(b))
    }

  implicit def mapAnyRefBindable[K: Show, T <: AnyRef]: Bindable[Map[K, T]] =
    m => SBindings(m.map(t => t.leftMap(_.show)))

  implicit def mapShowBindable[K: Show]: Bindable[Map[K, Int]] =
    Bindable[Map[String, Object]].contramap(mp =>
      mp.map(t => t.bimap(_.show, x => x: java.lang.Integer)).toMap
    )

  implicit val collectionBindable: Bindable[Collection] =
    Bindable[Map[String, String]].contramap { c =>
      Map("collection" -> c.name, "sort" -> c.preferredSort.toString)
    }

  implicit val bookBindable: Bindable[BookInput] =
    Bindable[Map[String, AnyRef]].contramap { b =>
      Map("title" -> b.title, "isbn" -> b.isbn, "authors" -> b.authors)
    }
}
