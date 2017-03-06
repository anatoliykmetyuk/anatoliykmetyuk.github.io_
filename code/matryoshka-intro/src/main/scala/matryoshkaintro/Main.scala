package matryoshkaintro

import scala.language.postfixOps

import scalaz._

import matryoshka._
import matryoshka.data.Fix
import matryoshka.implicits._

object Main extends NatFramework with ListFComponent with CataComponent {
  def main(args: Array[String]): Unit = {

    // def foldRight[A, B](l: List[A], initial: B, combine: (A, B) => B): B = l match {
    //   case a :: ax => combine(a, foldRight(ax, initial, combine))
    //   case Nil     => initial
    // }

    // def foldRight[Structure, A, B](structure: Structure, head: Structure => A, tail: Structure => Option[Structure], initial: B, combine: (A, B) => B): B =
    //   tail(structure) match {
    //     case Some(ax) => combine(head(structure), foldRight(ax, head, tail, initial, combine))
    //     case None     => initial
    //   }

    // println(
    //   foldRight[List[Int], Int, Int](
    //     structure = List(1, 2, 3)
    //   , head      = _.head
    //   , tail      = _ match { case _ :: ax => Some(ax) case Nil => None }
    //   , initial   = 0
    //   , combine   = _ + _
    //   )
    // )  // 6

    // println(
    //   foldRight[Nat, Unit, Int](
    //     structure = Succ(Succ(Succ(Zero)))
    //   , head      = _ => ()
    //   , tail      = _ match { case Succ(x) => Some(x) case Zero => None }
    //   , initial   = 0
    //   , combine   = { (_, sum) => 1 + sum }
    //   )
    // )  // 3

    // start snippet cool-thingy
    // List
    type ListOfInts[T] = ListF[Int, T]
    val list: Fix[ListOfInts] = Fix(ConsF(1, Fix(ConsF(2, Fix(ConsF(3, Fix[ListOfInts](NilF)))))))

    val listResult: Int = cata[ListOfInts, Int](list) {
      case ConsF(a: Int, x: Int) => a + x
      case NilF                  => 0
    }

    println(listResult)  // 6
    // end snippet cool-thingy

    // Nat
    val threeNat: Fix[Nat] = Fix(Succ(Fix(Succ(Fix(Succ(Fix[Nat](Zero)))))))

    val natResult: Int = cata[Nat, Int](threeNat) {
      case Succ(x: Int) => x + 1
      case Zero         => 0
    }

    println(natResult)  // 3
  }
}

trait CataComponent {
  case class Fix[F[_]](unfix: F[Fix[F]])

  def cata[F[_], A](structure: Fix[F])(algebra: F[A] => A)(implicit F: scalaz.Functor[F]): A =
    algebra( F.map(structure.unfix)(cata(_)(algebra)) )
}

trait ListFComponent {
  trait ListF[+A, +T]
  case class ConsF[A, T](a: A, t: T) extends ListF[A, T]
  case object NilF extends ListF[Nothing, Nothing]

  implicit def listfBifunctor: Bifunctor[ListF] = new scalaz.Bifunctor[ListF] {
    def bimap[A, B, C, D](fab: ListF[A, B])(f: A => C, g: B => D): ListF[C, D] = fab match {
      case ConsF(a, b) => ConsF[C, D](f(a), g(b))
      case NilF        => NilF
    }
  }

  implicit def listfRightFunctor[A]: Functor[ListF[A, ?]] = listfBifunctor.rightFunctor[A]
}

trait NatList {
  type Nat          = List[Unit]
  def  Succ(x: Nat) = () :: x
  def  Zero         = Nil

  def toNat(x: Int): Nat = if (x <= 0) Zero else Succ(toNat(x - 1))
  def toInt(x: Nat): Int = x match {
    case _ :: ax => 1 + toInt(ax)
    case Nil     => 0
  }

  def betterToInt(x: Nat): Int = x.foldRight(0) { (a, res) => 1 + res }
}

trait NatRecursive {
  trait Nat
  case class  Succ(x: Nat) extends Nat
  case object Zero extends Nat

  def toNat(x: Int): Nat = if (x <= 0) Zero else Succ(toNat(x - 1))
  def toInt(x: Nat): Int = x match {
    case Succ(y) => 1 + toInt(y)
    case Zero    => 0
  }
}

trait NatFramework { base =>
  trait Nat[+A]
  case class  Succ[A](x: A) extends Nat[A]
  case object Zero extends Nat[Nothing]

  implicit val natFunctor: Functor[Nat] = new Functor[Nat] {
    override def map[A, B](x: Nat[A])(f: A => B): Nat[B] = x match {
      case Succ(x) => Succ(f(x))
      case Zero    => Zero
    }
  }

  type FN = Fix[Nat]

  implicit class IntOps(x: Int) {
    def toNat: FN = base.toNat[FN](x)
  }

  implicit class NatFixOps(x: FN) {
    def toInt: Int = base.toInt[FN](x)

    def |+|(x2: FN) = add (x, x2)
    def  * (x2: FN) = mult(x, x2)
    def !           = fact(x)
  }

  def toInt[T](x: T)(implicit T: Recursive.Aux[T, Nat]): Int =  // Can't use kind projector, since the syntax implicits don't infer it
    x.cata[Int] { case Succ(x) => x + 1; case Zero => 0 }

  def toNat[T](x: Int)(implicit T: Corecursive.Aux[T, Nat]): T =
    x.ana[T] { case x: Int if x > 0 => Succ(x - 1); case _ => Zero }

  def add[T](x1: T, x2: T)(implicit TR: Recursive.Aux[T, Nat], TC: Corecursive.Aux[T, Nat]): T =  // Can't use Birecursive here, since the syntax implicits don't infer it
    x1.cata[T] { case x: Succ[T] => x.embed; case Zero => x2 }

  def mult[T](x1: T, x2: T)(implicit TR: Recursive.Aux[T, Nat], TC: Corecursive.Aux[T, Nat]): T =
    x1.cata[T] { case Succ(res) => add(res, x2); case Zero => Zero.embed }

  def fact[T](x: T)(implicit TR: Recursive.Aux[T, Nat], TC: Corecursive.Aux[T, Nat]): T =
    x.para[T] { case Succ((t, res)) => mult(res, Succ(t).embed); case Zero => Succ[T](Zero.embed).embed }
}

object NatFramework extends NatFramework
