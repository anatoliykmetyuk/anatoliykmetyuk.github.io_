package matryoshkaintro

object C1NonDry extends App with C1Defs {
  // Nat to Int
  def natToInt(n: Nat): Int = n match {
    case Succ(x) => 1 + natToInt(x)
    case Zero    => 0
  }
  val nat = Succ(Succ(Succ(Zero)))
  val natRes: Int = natToInt( nat )
  println(natRes)  // 3

  // Sum a list of ints
  def sumList(l: IntList): Int = l match {
    case Cons(head, tail) => head + sumList(tail)
    case Empty            => 0
  }
  val lst = Cons(1, Cons(2, Cons(3, Empty)))
  val listRes: Int = sumList( lst )
  println(listRes)  // 6

  // Evaluate an expression
  def eval(e: Expr): Int = e match {
    case Add (x1, x2) => eval(x1) + eval(x2)
    case Mult(x1, x2) => eval(x1) * eval(x2)
    case Num (x)      => x
  }
  val expr = Add(Mult(Num(2), Num(3)), Num(3))
  val exprRes = eval( expr )
  println(exprRes)  // 9
}

trait C1Defs {
  // Nat
  sealed trait Nat
  case class   Succ(x: Nat) extends Nat
  case object  Zero         extends Nat

  // List
  sealed trait IntList
  case class   Cons(head: Int, tail: IntList) extends IntList
  case object  Empty extends IntList

  // Expr
  sealed trait Expr
  case class   Add (x1: Expr, x2: Expr) extends Expr
  case class   Mult(x1: Expr, x2: Expr) extends Expr
  case class   Num (x : Int           ) extends Expr
}