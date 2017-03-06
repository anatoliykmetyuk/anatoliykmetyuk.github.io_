package shapelesspoly

import shapeless._
import poly._

object ShapelessPoly extends App {

  object f extends Poly1 {
    implicit val intCase    = at[Int   ] { x => "It Works! " * x}
    implicit val stringCase = at[String] { x => x.length        }
  }

  println(f(3))
  println(f("Foo"))
}
