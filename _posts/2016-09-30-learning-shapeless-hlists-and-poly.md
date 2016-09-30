---
layout: post
title: Learning Shapeless&#58; HLists and Poly
categories:
- blog
---
Shapeless is a Scala library that aims to make programming more type safe. This means that you let the compiler know as much about your program as possible, so that if something is wrong with it, it is more likely to be caught on compile time.

Since a lot of the information about a program is represented by types, Shapeless utilizes them heavily to achieve its goal.

In this post, I will cover the basics of Shapeless `HList`s.

# Introduction
An `HList` is a heterogenous list. It is a generalization of the Scala tuples. While ordinary Scala `List`s can contain only one type of elements (`List[Int]` for `Int`s, `List[String]` for `String`s etc), `HLists` can contain many. This is done in a typesafe way: the compiler knows about the types of the elements in the list.

Here is a simple `HList` (make sure to `import shapeless._` before running the examples):

```scala
scala> val hlist = 1 :: 2 :: "foo" :: 3.5 :: HNil
hlist: shapeless.::[Int,shapeless.::[Int,shapeless.::[String,shapeless.::[Double,shapeless.HNil]]]] = 1 :: 2 :: foo :: 3.5 :: HNil
```

## The head of an empty list
Consider an example where we want to get a head of a list. Some lists are empty, and this edge case should be handled somehow.

```scala
scala> List(1).head
res0: Int = 1

scala> Nil.head
java.util.NoSuchElementException: head of empty list
  at scala.collection.immutable.Nil$.head(List.scala:420)
  ... 42 elided

scala> (1 :: HNil).head
res2: Int = 1

scala> HNil.head
<console>:15: error: could not find implicit value for parameter c: shapeless.ops.hlist.IsHCons[shapeless.HNil.type]
       HNil.head
```

A normal Scala `List` throws an exception during the runtime, but in case of `HNil`, a compilation error happens. Hence, with a `List`, you won't know about the error until you run the program and this piece of code gets executed. In case of `HList`, the error will be identified during the compilation, allowing you to fix it right away and reduce the risks of bugs.

## HList architecture
Consider our previous example:

```scala
scala> val hlist = 1 :: 2 :: "foo" :: 3.5 :: HNil
hlist: shapeless.::[Int,shapeless.::[Int,shapeless.::[String,shapeless.::[Double,shapeless.HNil]]]] = 1 :: 2 :: foo :: 3.5 :: HNil
```

The most interesting thing here is the type of this object and how it is constructed. It is a `shapeless.::`, recursive in its right hand side argument. This type is defined as follows:

```scala
sealed trait HList extends Product with Serializable

final case class ::[+H, +T <: HList](head : H, tail : T) extends HList
```

There are two methods to help you construct `HList`s: one for `HNil`, which is an empty list, and one for `HList`, which is an arbitrary list:

```scala
trait HNil extends HList {
  def ::[H](h : H) = shapeless.::(h, this)
}

obejct HNil extends HNil

trait HListOps {
  def ::[H](h : H) : H :: L = shapeless.::(h, l)
}
```
With `::`, it is possible to construct lists by induction:

- Given arbitrary `H` and `T <: HList`, we can construct `H :: T`.
- An instance of an empty list `HNil` exists.

This pattern of building types inductively is observed in many parts of Shapeless. It allows to use simple primitives and inductive definitions to build complex types on compile time recursively.

# List operations
## Architecture
To learn about what you can do with `HLists`, you'll want to have look at HList's [syntax](https://github.com/milessabin/shapeless/blob/master/core/src/main/scala/shapeless/syntax/hlists.scala) and [operations](https://github.com/milessabin/shapeless/blob/master/core/src/main/scala/shapeless/ops/hlists.scala) sources. Since there are a lot of operations defined there, it makes sense to understand the general architecture and patterns that are used.

### Syntax
The [syntax](https://github.com/milessabin/shapeless/blob/master/core/src/main/scala/shapeless/syntax/hlists.scala) file features a `HListOps` class, to which `HLists` are implicitly converted. This class contains methods similar to the ones you can find in an ordinary Scala `List`.

The operations defined in `HListOps` follow a common pattern. For example:

```scala
def head(implicit c : IsHCons[L]) : c.H = c.head(l)
```

This method returns the first element of a list. Some observations to make here:

- The `IsHCons` implicit is a **typeclass** that encapsulates the required behaviour, and all the work is done by it. In this case, `IsHCons[L]` defines how to split `L <: HList` to a head and a tail. All the operations on `HList`s are defined by typeclasses in a similar manner.
- This typeclass is **parameterised by `L`** which is the type of the `HList` the operation is called upon. This is how Shapeless provides type safety: if there's no appropriate typeclass in scope, the operation is impossible and a compile-time error will happen.
- The return type `c.H` of the method is defined by the typeclass. Hence **the logic responsible for `implicit` resolution is also responsible for the output type computation**. The more complex this logic is, the more complex computations with types are possible on compile time.


### Operations
The [operations](https://github.com/milessabin/shapeless/blob/master/core/src/main/scala/shapeless/ops/hlists.scala) file contains all the typeclasses required by the `syntax`: their `trait`s and `implicit def`s to resolve them.

To understand their architecture, let's look at an example of `IsHCons` typeclass. This is a typeclass required implicitly by the `head` method discussed above, and it defines how to split a list `L` into a head `H` and a tail `T <: HList`:

```scala
  trait IsHCons[L <: HList] extends Serializable {
    type H
    type T <: HList

    def head(l : L) : H
    def tail(l : L) : T
  }
  
  object IsHCons {
    def apply[L <: HList](implicit isHCons: IsHCons[L]): Aux[L, isHCons.H, isHCons.T] = isHCons

    type Aux[L <: HList, H0, T0 <: HList] = IsHCons[L] { type H = H0; type T = T0 }
    implicit def hlistIsHCons[H0, T0 <: HList]: Aux[H0 :: T0, H0, T0] =
      new IsHCons[H0 :: T0] {
        type H = H0
        type T = T0

        def head(l : H0 :: T0) : H = l.head
        def tail(l : H0 :: T0) : T = l.tail
      }
  }
```

Some observations to be made here:

- A typeclass in Shapeless usually is a `trait`, type-parameterized by the types of its input.
- Output types are usually defined via the `type` keyword.
- Typeclasses have companion objects. They usually consist of the following:
  - An `Aux` type definition to easily define the output types of the typeclasses.
  - An `apply` method to conveniently resolve a typeclass instance.
  - One or more `implicit def` that output the typeclass instances.

## The head of an empty list example internals
Consider our example with a head of an empty list discussed above. How exactly does the compiler verify that an operation is possible for `Int :: HNil`, but is not possible in case of `HNil`?

First, the target `HList` is converted implicitly to `HListOps`, which has the `head` method:

```scala
def head(implicit c : IsHCons[L]) : c.H = c.head(l)
```

This method can be called only if the typeclass it requires can be found.

There's one `implicit def` in the companion object of `IsHCons`, it has the following signature:

```scala
implicit def hlistIsHCons[H0, T0 <: HList]: IsHCons.Aux[H0 :: T0, H0, T0]
```

In case of `Int :: HNil`, the required typeclass type is `IsHCons[Int :: HNil]`, and `IsHCons.Aux[H0 :: T0, H0, T0]` conforms to it, because it is an alias for `IsHCons[H0 :: T0]` with `H0` and `T0` output types (one for the head and one for the tail). So the implicit is found and the compilation succeeds.

In case of `HNil`, however, the required typeclass has the type of `IsHCons[HNil]`. Since `IsHCons.Aux[H0 :: T0, H0, T0]` does not conform to this type (because `HNil` cannot be represented as `H0 :: T0`), the compiler cannot find the typeclass and the compilation fails.

## Length of `HList`s and type-level natural numbers
Recall that the output types of the `HList` operations are often defined by the typeclasses, which are resolved implicitly. Hence, one can argue that the output types are computed by the same capabilities that resolve the implicits, and the complexity of the computations you can do with the types in such a manner is proportional to the complexity of the implicits' resolution mechanics.

In Shapeless, a common pattern is implicit `def`s that themselves require some implicit typeclasses. For example, here is what implicits are available for the `Length` typeclass, which computes the length of a given `HList`:

```scala
implicit def hnilLength[L <: HNil]: Length.Aux[L, _0]

implicit def hlistLength[H, T <: HList, N <: Nat](implicit lt : Length.Aux[T, N], sn : Witness.Aux[Succ[N]]): Length.Aux[H :: T, Succ[N]]
```

A key thing to notice here is that we have a `def` for `HNil` in scope and a `def` for an arbitrary `HList` of the form `H :: T`. Thus, the lengths are defined inductively:

- A length of a list is the length of its tail plus one (the length of `T` is defined by the implicit typeclass that is required).
- A length of an empty list is 0

Hence, for a list of length N, N `Length` typeclasses are needed to be resolved. For example, `Length[Int :: Int :: HNil]` requires `Length[Int :: HNil]`, which requires `Length[HNil]`, which is `hnilLength`.

The recursive nature of the resolution of implicits and the fact that the computations on types can be carried out using the same mechanics gives a natural rise to the type-level representations of the natural numbers. `Nat` is their common trait, `_0 <: Nat` is `0`, and `Succ[N <: Nat]` represents the successor of `N`.

Now, the length of `HNil` is `_0` - the input type is `L` and the output is `_0`. The length of `H :: T`, `T <: HList`, is `Succ[N]`, where `N` is bound by `Length.Aux[T, N]`.