---
layout: post
title: Process algebra as expression rewriting
categories:
- blog
---
# Process algebra as expression rewriting

This is a progress report on my attempt to model a process algebra as an expression rewriting machine. The process algebra in question is [SubScript](http://subscript-lang.org/from-acp-and-scala-to-subscript/), {E}:PAPERS which is an extension to [ACP](https://en.wikipedia.org/wiki/Algebra_of_Communicating_Processes) (it is highly recommended to familiarize yourself with ACP and [process algebras](https://en.wikipedia.org/wiki/Process_calculus) before reading this article further).

## Motivation
The idea of a process algebra engine is to translate a process algebra expression into a set of side effects that happen in a coordinated manner. Hence, the main job of such an engine is to coordinate the execution of processes to ensure that they happen in a manner described by the user. {E}:EXAMPLES

The current [implementation](https://github.com/scala-subscript/subscript) of SubScript in Scala achieves this by employing an actor model. Every process algebra entity, such as operator or atomic action, is represented by its own node (actor) in an execution graph (actor hierarchy). Operators become actors that supervise other actors - operands of these operators.

The lower-level actors report all the events that happen to them to their supervisors, and based on this information the supervisors coordinate the execution of their subordinates. {E}:EXAMPLES,PICTURE

However, actors are hard to explore in mathematical fashion. This is why, the motivation for an alternative implementation of SubScript, [FreeACP](https://github.com/anatoliykmetyuk/free-acp), is to define an engine for SubScript in mathematical terms rather than engineering ones.

## Theory
While the standard implementation of SubScript uses the actor model, FreeACP rewrites SubScript expressions according to its axioms, executing side effects during these rewritings, to achieve the desired semantics.

### Suspended computations
In process algebra, the decisions on how to proceed with the execution of an expression are usually made based on what the individual operands evaluate to {E:EXAMPLE}. These results can be available immediately, or we may need to wait for them {E:EXAMPLE}. In the latter case, we deal with a **suspended computation**.

Whenever we have a suspended computation and need its result `x` to compute some expression `y`, we create a function `x => y` that is able to compute `y` from `x`, and then *map* the suspended computation of `x` to get a suspended computation of `y`.

In the language of Category Theory, if we have `S[X]` and `X => Y`, and if `S` is a functor, we can map `X => Y` to `S[X] => S[Y]`.

### Axioms
Hence, there are two kinds of axioms: the rewriting axioms and the suspension axioms. The latter are used in situations when we need to wait for some potentially long-running computation to finish to do the rewriting; the former are for situations when this is not the case.

For example, this is a set of axioms for the sequential composition of processes:

\[\color{blue} {Rewriting\ axioms}\\
[*]() = ε \\
[*]([*](x) :: y) = [*](x :: y) \\
[*](ε :: x) = x \\
[*](δ :: x) = δ \\
\color{blue} {Suspension\ axioms}\\
\frac{[*](a :: x)}{a: ε \rightarrow [*](x);\ δ \rightarrow δ}\]
<script type="text/javascript" src="http://www.hostmath.com/Math/MathJax.js?config=OK"></script>

A sequence is presented as `[*](list)`, where `list` is a list of expressions, and `::` is concatenation.

The rewriting axioms are of the form `a = b`.

The suspension axioms work on the trees for which there are some unknowns to compute. They have a horizontal bar, above which is the pattern with one or more unknown elements that need to be computed. Below the bar, there are instructions on how to map the result of the unknown element execution to the new tree which we will rewrite the current one with.

It is possible to simplify expression like `εx` without any "heavy" execution, using a rewriting axiom: empty action `ε` followed by some process `x` is the same as `x`. However, for some atom `a`, we can not simplify `ax` without knowing the outcome of `a` - so a suspension axiom is used here.

### Execution semantics
A process algebra engine which works via expression rewriting takes an expression tree and a set of rewriting and suspension axioms as an input and follows the following algorithm:

1. If a suspension axiom is applicable, apply it and wait for the resulting tree {E:HOW DO SUSPENSION AXIOMS WORK?}. Once the resulting tree is available, restart this algorithm recursively with this tree as an input.
2. Otherwise, if the input tree is a terminal element (either `ε` or `δ`), return it.
3. Otherwise, apply a rewrite axiom and recursively feed the result to this algorithm.

So, the rewriting axioms will be invoked on a tree until a suspension axiom is available, in which case it will be invoked. This continues until the tree is simplified to ε - successful result - or δ - failure.

# Architecture
## Tree
Process algebra expressions are modeled as ordinary [Tree](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L6)s. A `Tree` has a higher-kinded type argument to it, `S[_]`. `S` is the type a suspended computation's result {E:SUSPENDED COMPUTATION?} gets boxed into (think of `Future[_]`).

There are following notorious subclasses of `Tree`:

- Operators: [Sequence](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L85) and [Choice](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L86).
- Terminal cases: [Result](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L88) and its subclasses: `Success` and `Failure`, representing ε and δ respectively.
- [Suspend](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L83) - carries a suspended computation.

A suspended computation is defined as `S[Tree[S]]` and should be interpreted as follows: there is some computation to run under `S` (however this `S` dictates to run it) and the result of it is another process algebra expression `Tree[S]`. For example, an ordinary atomic action can be represented as `S[Result[S]]` and should be read as follows: there's some action to run under `S`, and if it is successful (no exception happens), the result is `ε`, otherwise - δ.

## Execution
Sets of [rewriting](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L12) and [suspension](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L44) axioms are defined as partial functions and are straightforward to read.

Some points to note about them:

- Rewriting axioms take a `Tree[S]` and return a `Tree[S]` as a result - they just rewrite a given tree.
- Suspension axioms take a `Tree[S]` and return a `List[S[Tree[S]]]`. `List` reflects that there may be a need to make a choice between several trees (choice together with sequence is one of the fundamental operators of ACP). `S[Tree[S]]` means that the trees which the current one should be rewritten to are not readily available and are computed by `S`.

These axioms are applied in a [loop](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L67) until a terminal case is reached, as described in the theory above.

## Suspension type as a free object
If one has a `Tree[S]`, they do not have much choice but to execute it under `S`. This may not always be desirable: for instance, one may have a Tree[[Eval](https://static.javadoc.io/org.typelevel/cats-core_2.12/0.8.1/cats/Eval$.html)], but want to execute it with multithreading via `Future`.

For this reason, the function that runs the trees, [runM](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L65), can take a natural transformation, `S ~> G`, using which one can specify how to execute `S` under `G`.

Further increasing flexibility, we may even have a default implementation of `S` as a free object, in style of the [free monad](http://typelevel.org/cats/datatypes/freemonad.html).

### `LanguageT` as a free S
The pattern as follows. All the expressions for FreeACP are written with [LanguageT](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Language.scala) as `S` by default: `Tree[LanguageT]`.

`LanguageT` does not do anything by itself, but remembers the operations you tried to perform on it. It does this by [reifying](https://en.wikipedia.org/wiki/Reification_(computer_science)) all the operations done on it into case classes. For example, `t.map(f) == MapLanguage(t, f)`.

Next, the user can select whichever `S` they want to execute their program under, define a natural transformation `LanguageT ~> S` (roughly, `LanguageT[Tree[LanguageT]] => S[Tree[LanguageT]]`) and use it in the `runM` method to execute the `LanguageT` instances. This natural transformation is called the **compiler**, it compiles your program written under `LanguageT` to a concrete execution type `S`.

### Compilers for the `LanguageT`
#### Default compiler
There is a number of default subclasses of `LanguageT` that reify operations that are used in the suspension axioms (`map: (A => B) => (LanguageT[A] => LanguageT[B])` and `suspend: A => LanguageT[A])` and hence are necessary for the `LanguageT` to be used as a suspension type.

Hence, there is also a [default compiler](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Language.scala#L38) for such subclasses (I will explain the `PartialCompiler[F]` type in a moment, but it is in essence a `LanguageT ~> F` natural transformation).

Internally, the compiler is a partial function, specifying how to translate various subclasses of `LanguageT` to `F`. For example, `case MapLanguage(t1, f)   => F.map(mainCompiler(t1))(f)` is the line handling `MapLanguage`. All it does is mapping `t1` by `f` using a `F.map` where variable `F` is of type `Functor[F]`. In essence, the compiler describes how to *delegate* the `map` to the functor of `F`, whatever this `F` is.

In its definition, the default compiler declares some implicit arguments: `implicit F: Functor[F], S: Suspended[F]`. This means that internally it delegates some operations to a `Functor` and a `Suspend` type classes, so if they are not in scope, there's nothing to delegate to and hence one can't get the compiler for `F`. This also works another way around: whatever your `F`, if you have a `Suspended` and a `Functor` for it, you will be able to compile your program under that `F`.

One can get the default compiler for `F` if and only if one has `Functor[F]` and `Suspended[F]`.

#### Compilers framework
It is possible to compose compilers for different subclasses of `LanguageT`. One can define their own subclass `LanguageT` and add a compiler for that subclass, this way extending the expressive power they have.

By default, one can only use an [atomic action](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Language.scala#L15) to create a suspended computation, but one can define more primitives to write process algebra expressions with.

A simplest example is a [say](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/component/Say.scala#L9) primitive that just prints something to the console. The pattern for `say` is as follows: first, we define a subclass of `LanguageT` - wherever we use it, we want it to mean "print the payload to the console". Next, we define a [compiler](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/component/Say.scala#L11) capable of executing that subclass under some `F`. This compiler has a very similar structure to the default compiler, except that it can only handle [one case](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/component/Say.scala#L13) of the language: `SayContainer`.

Once defined, all the compilers can be composed to form a single compiler to be used in `runM`. This is done via the [compiler](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Language.scala#L34) function. Since all the "small" compilers return an `Option[F[Tree[LanguageT]]]`, the "large" compiler iterates through the list of the "small" ones until a `Some(_)` is returned.

Also, the "small" compilers, or [PartialCompiler](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Language.scala#L32)s are of type `Compiler[F] => LanguageT ~> OptionK[F, ?]` (and `Compiler[F]` is `LanguageT ~> F`). This means that we are able to use the "large" compiler from the "small" ones, so that we can invoke it recursively.