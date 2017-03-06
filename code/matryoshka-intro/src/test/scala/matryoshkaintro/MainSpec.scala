package matryoshkaintro

import org.scalacheck.Properties
import org.scalacheck.Prop.{ forAll, BooleanOperators }
import org.scalacheck.Gen.{ choose, zip }

import matryoshka._
import matryoshka.data.Fix
import matryoshka.implicits._

object MainSpec extends Properties("MainSpec") with NatFramework {

  type T = Fix[Nat]

  def nats       = choose(0, 100)
  def pairs      = zip(nats, nats)
  def smallNats  = choose(0, 5)
  def smallPairs = zip(smallNats, smallNats)

  def factInt(x: Int): Int =
    if (x <= 0) 1 else x * factInt(x - 1)

  property("to-nat and from-nat should be isomorphic") =
    forAll(nats) { x => x == toInt[T](toNat[T](x)) }

  property("addition") =
    forAll(pairs) { case (x, y) => toInt[T](add[T](toNat[T](x), toNat[T](y))) == x + y }

  property("multiplication") =
    forAll(smallPairs) { case (x, y) => toInt[T](mult[T](toNat[T](x), toNat[T](y))) == x * y }

  property("factorial") =
    forAll(smallNats) { x => toInt[T](fact[T](toNat[T](x))) == factInt(x) }

}
