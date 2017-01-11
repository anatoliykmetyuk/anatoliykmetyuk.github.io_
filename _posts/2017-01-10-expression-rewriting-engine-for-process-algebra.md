---
layout: post
title: Expression rewriting engine for process algebra
categories:
- blog
---
# Expression rewriting engine for process algebra

This is a progress report on my attempt to model a process algebra as an expression rewriting machine. The process algebra in question is [SubScript](http://subscript-lang.org/from-acp-and-scala-to-subscript/)[^1][^2], which is an extension to [ACP](https://en.wikipedia.org/wiki/Algebra_of_Communicating_Processes) (it is highly recommended to familiarize yourself with ACP and [process algebras](https://en.wikipedia.org/wiki/Process_calculus) before reading this article further).

[^1]: [http://subscript-lang.org/papers/subscript-white-paper/](http://subscript-lang.org/papers/subscript-white-paper/)
[^2]: [https://arxiv.org/abs/1504.03719](https://arxiv.org/abs/1504.03719)

## Process algebra expressions

### Theory
A **process algebra (PA) expression** consists of atomic actions (AAs), special operands and operators. It describes some process with the help of these elements as follows.

**AAs** specify what to do, they can be executed. During their execution, side effects can occur and the result of such an execution is either **success** (denoted **ε**) if the computation proceeded as planned, or **failure (δ)** if there were errors (in JVM languages, exceptions thrown) in the process.

ε and δ are two fundamental **special operands**.

**Operators** define in which order AAs are to be executed and how their outcomes influence one another. The behavior of an operator is its **semantics**.

### Example
Here is an example of how this theory can be used in a context of a Scala GUI application controller. Consider a GUI application with two buttons, `first` an `second`, and a text field `textField`. If `first` button was pressed, you want to set `textField`'s text to "Hello World", if `second` was pressed - to "Something Else".

This can be described by the following PA expression: `button(first) * gui {textField.text = "Hello World"} + button(second) * gui {textField.text = "Something Else"}`.

Consider `button(btn)` to specify an atomic action that happens when the button `btn` is pressed. Consider `gui(code)` to specify an AA that executes `code` in the GUI thread of the GUI framework in question, and `textField.text = "foo"` is used to set the text of some GUI element visible to the user.

`*` is a sequential operator, specifying that its operands should execute one after another.

`+` is a choice operator, its precedence is lower than that of `*`. Given two arbitrary PA expressions as its operands, the first one to start (have an AA successfully evaluate to ε in it) will be chosen for full execution and the other one will be discarded.

Hence, in the example above, if the button `first` is pressed, the following will happen:

1. `button(first)` will evaluate to `ε`.
2. Since one of its operands have started, `+` will discard the `button(second) * gui {textField.text = "Something Else"}` - now, even the user presses the `second` button, `gui {textField.text = "Something Else"}` has no chance of being executed.
3. According to `*` in `button(first) * gui {textField.text = "Hello World"}`, its second operand must be executed after the successful execution of the first one. Since this has happened, `gui {textField.text = "Hello World"}` will be executed, setting `testField.text` to "Hello World".

## Motivation
The idea of a PA engine is to execute a PA expression according to the rules of the particular PA in question. The main job of such an engine is to implement the semantics of the PA entities - what an operator should do, how to execute an AA etc.

### Standard SubScript implementation
The standard [implementation](https://github.com/scala-subscript/subscript) of SubScript in Scala takes the *coordinating* approach to the problem with the help of the actor model.

Every process algebra entity, such as operator or atomic action, is represented by its own node (actor) in an execution graph (actor hierarchy). Operators become actors that supervise other actors - operands of these operators. The lower-level actors report all the events that happen to them to their supervisors, and based on this information the supervisors coordinate the execution of their subordinates.

### Example
Let us have a look at how our GUI example above would have been executed in the standard implementation:

1. A hierarchy of actors (nodes) is created: <img src="/media/expression-rewriting-engine-for-process-algebra/SubScriptActors.svg" alt="Diagram" width="650" onclick="window.open(this.src)" onmouseover="this.style.cursor='pointer'"/>

2. When the button `first` is pressed, it sends a message to its supervising actor, `*`, which in turn forwards it to `+`. Both these actors have the information that `first` have successfully finished at this point.
3. `+` acts upon this information by cancelling its second operand
4. `*` acts upon this information by instantiating an actor corresponding to its second operand and sending a message to it, ordering it to start execution

### Problem

However, actors and communication between them are hard to explore in mathematical fashion. This is why, the motivation for an alternative implementation of SubScript, [FreeACP](https://github.com/anatoliykmetyuk/free-acp), is to define an engine for SubScript in mathematical terms rather than engineering ones.

## Theory
While the standard implementation of SubScript uses the coordinating approach discussed above, FreeACP uses the **rewriting** approach. Its idea is to rewrite SubScript expressions according to the axioms of this algebra, gradually simplifying them. Eventually, every expression is reduced to either ε or δ.

In the process of rewritings, the AAs are evaluated (executed) on need and hence reduced to either ε or δ at the expense of side effects their evaluation can produce.

### Example
Let us see how our GUI example behaves under the rewriting approach.

First, we need to define the axioms we are working under:

<img src="/media/expression-rewriting-engine-for-process-algebra/axioms-1.gif" alt="Diagram" onclick="window.open(this.src)" onmouseover="this.style.cursor='pointer'"/>

Under (1) axiom, we need to evaluate the first AAs of both processes, see which one has completed first and rewrite the expression according to the axiom

Assume `button(first)` was completed first. By `(1)`, the expression will be rewritten to `gui {textField.text = "Hello World"}`, which by `(3)` is equal to `gui {textField.text = "Hello World"} * ε`. By `(2)`, the `gui {/*...*/}` will be evaluated. Assuming it evaluated to `ε`, by `(2)` the expression will rewrite to `ε * ε`, which by `(3)` is just `ε`. This way, we computed the result of the original PA expression to be `ε`.

### Suspended computations
Some AAs can take time to evaluate. For example, in case of `button(first)`, it takes time for a user to press the button. Hence, the result of such an AA is not readily available. However, it is often necessary to know it to proceed with the rewritings. This is a **suspended computation**.

Functional programming offers a standard way of treatment for such a scenario: if the result of the computation is `A`, it is wrapped into a suspension type and becomes `S[A]`. If `S` is a functor, we can map `S[A]` and apply the rewriting rule within the map function as if we already know the result `A`. After the mapping, we get an `S[Tree]`, where `Tree` is an expression this one should be rewritten to once `A` is available.

### Axioms
Hence, there are two kinds of axioms: rewriting axioms and suspension axioms. The latter are used in situations when we need to wait for some potentially long-running computation to finish to do the rewriting; the former are for situations when this is not the case.

For example, this is a set of axioms for the sequential composition of processes:

<img src="/media/expression-rewriting-engine-for-process-algebra/axioms-2.gif" alt="Diagram" onclick="window.open(this.src)" onmouseover="this.style.cursor='pointer'"/>

A sequence is presented as `[*](list)`, where `list` is a list of expressions, and `::` is concatenation.

The rewriting axioms are of the form `a = b`.

The suspension axioms work on the trees for which there are some suspended computations to compute before it is possible to apply rewriting axioms. They have a horizontal bar, above which is the pattern with one or more unknown elements that need to be computed. Below the bar, there are instructions on how to map the result of the unknown element execution to the new tree which we will rewrite the current one with.

It is possible to simplify expression like `εx` without any "heavy" execution, using a rewriting axiom: empty action `ε` followed by some process `x` is the same as `x`. However, for some atom `a`, we can not simplify `ax` without knowing the outcome of `a`. So a suspension axiom is used here to compute the result `r` of `a`, and the rewriting rule is to substitute `r` in place of `a`.

Speaking Category Theory, given `a: S[A]` and a tree `a * x` that needs rewriting and this particular suspension axiom can work on, the rewritten tree is `a.map { r => r * x }`, of type `S[Tree]`.

### Execution semantics
A process algebra engine which works via expression rewriting invokes the rewriting axioms on the input tree until a suspension axiom is available, in which case the suspension axiom will be invoked. This continues until the tree is simplified to ε - success result - or δ - failure.

Precisely, the algorithm is as follows:

1. If a suspension axiom is applicable, apply it and wait for the resulting tree. Once the resulting tree is available, restart this algorithm recursively with this tree as an input.
2. Otherwise, if the input tree is a terminal element (either `ε` or `δ`), return it.
3. Otherwise, apply a rewrite axiom and recursively feed the result to this algorithm.

## Architecture

### Tree
Process algebra expressions are modeled as ordinary [Tree](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L6)s. A `Tree` has a higher-kinded type argument to it, `S[_]`. `S` is a type a suspended computation's result gets boxed into (can be `Future[_]`, for example).

There are following notorious subclasses of `Tree`:

- Operators: [Sequence](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L85) and [Choice](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L86).
- Terminal cases: [Result](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L88) and its subclasses: `Success` and `Failure`, representing ε and δ respectively.
- [Suspend](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L83) - carries a suspended computation.

A suspended computation is carried by `Suspend` nodes as `S[Tree[S]]` and should be interpreted as follows: there is some computation to running under `S` and the result of this computation is another process algebra expression `Tree[S]`.

For example, an ordinary atomic action can be represented as `S[Result[S]]` and should be read as follows: there's some action running under `S`, and if it is successful (no exception happens), the result is `ε`, otherwise - `δ`.

### Axioms
Sets of [rewriting](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L12) and [suspension](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L44) axioms are defined as partial functions and are straightforward to read.

Some points to note about them:

- Rewriting axioms take a `Tree[S]` and return a `Tree[S]` as a result - they just rewrite a given tree.
- Suspension axioms take a `Tree[S]` and return a `List[S[Tree[S]]]`. `List` reflects that there may be a need to make a choice between several trees (choice together with sequence is one of the fundamental operators of ACP). `S[Tree[S]]` means that the trees which the current one should be rewritten to are not readily available and are computed by `S`.

### Execution
These axioms are applied in a [loop](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L67) until a terminal case is reached, as described in the theory above.

### Suspension type as a free object
If one has a `Tree[S]`, they do not have much choice but to execute it under `S`. This may not always be desirable: for instance, one may have a Tree[[Eval](https://static.javadoc.io/org.typelevel/cats-core_2.12/0.8.1/cats/Eval$.html)], but want to execute it with multithreading via `Future`.

Another example is `gui(code)` AA from our example: we agreed that it runs the `code` under a GUI thread of a particular GUI framework we are working under - but what if we want our program to work under several GUI frameworks?

For this reason, the function that runs the trees, [runM](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Tree.scala#L65), can take a natural transformation, `S ~> G`, using which one can specify how to map a suspended computation `S` to a suspended computation `G`.

Further increasing flexibility, we may even have a default implementation of `S` as a [free object](https://en.wikipedia.org/wiki/Free_object), in style of the [free monad](http://typelevel.org/cats/datatypes/freemonad.html).

#### `LanguageT` as a free S
The pattern as follows. All the expressions for FreeACP are written with suspension type `S` equal to [LanguageT](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Language.scala) by default: `Tree[LanguageT]`.

`LanguageT` is a free object - it does not do anything by itself, but remembers the operations you tried to perform on it. It does this by [reifying](https://en.wikipedia.org/wiki/Reification_(computer_science)) all the operations done on it into case classes. For example, `t.map(f) == MapLanguage(t, f)`.

Next, the user can select whichever `S` they want to execute their program under, define a natural transformation `LanguageT ~> S` (roughly, `LanguageT[Tree[LanguageT]] => S[Tree[LanguageT]]`) and use it in the `runM` method to execute the `LanguageT` instances. This natural transformation is called the **compiler**, it compiles your program written under `LanguageT` to a concrete execution type `S`.

In our example of a GUI thread, one may define a `case class Gui(code: () => Unit) extends LanguageT[Result[LanguageT]]` which contains the code to be executed, and then have a different natural transformation `LanguageT ~> S` for each GUI framework they are working under. This way, a program dependent on GUI thread can be written once and be executed on many GUI frameworks.

### Compilers for the `LanguageT`
#### Default compiler
There is a number of default subclasses of `LanguageT` that reify operations that are used in the suspension axioms (recall that the suspension axioms rely on `map: (A => B) => (LanguageT[A] => LanguageT[B])` and `suspend: A => LanguageT[A])` and hence are necessary for the `LanguageT` to be used as a suspension type.

Hence, there is also a [default compiler](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Language.scala#L38) for such subclasses.

Internally, the compiler is a partial function, specifying how to translate various subclasses of `LanguageT` to `F`. For example, `case MapLanguage(t1, f)   => F.map(mainCompiler(t1))(f)` is the line handling `MapLanguage`, a reification of the `map` method called on `LanguageT`. All it does is mapping `t1` by `f` using a `F.map` where variable `F` is of type `Functor[F]`. In essence, the compiler describes how to *delegate* the `map` to the functor of `F`, whatever this `F` is.

In its definition, the default compiler declares some implicit arguments: `implicit F: Functor[F], S: Suspended[F]`. This means that internally it delegates some operations to a `Functor` and a `Suspend` type classes and hence depends on them. So if they are not in scope, there's nothing to delegate to and hence one can't instantiate the compiler for `F`. This also works another way around: whatever your `F`, if you have a `Suspended` and a `Functor` for it, you will be able to compile your program under that `F`.

*One can get the default compiler for `F` if and only if one has `Functor[F]` and `Suspended[F]`.*

#### Compilers framework
It is possible to compose compilers for different subclasses of `LanguageT`. One can define their own subclass `LanguageT` and add a compiler for that subclass, this way extending the expressive power they have.

By default, one can only use an [atomic action](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Language.scala#L15) to create a suspended computation, but one can define more primitives to write process algebra expressions with.

A simplest example is a [say](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/component/Say.scala#L9) primitive that just prints something to the console. The pattern for `say` is as follows: first, we define a subclass of `LanguageT` - wherever we use it, we want it to mean "print the payload to the console". Next, we define a [compiler](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/component/Say.scala#L11) capable of executing that subclass under some `F`. This compiler has a very similar structure to the default one, except that it can only handle [one case](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/component/Say.scala#L13) of the language: `SayContainer`.

Once defined, all the compilers can be composed to form a single compiler to be used in `runM`. This is done via the [compiler](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Language.scala#L34) function. Since all the "small" compilers return an `Option[F[Tree[LanguageT]]]`, the "large" compiler iterates through the list of the "small" ones until a `Some(_)` is returned.

Also, the "small" compilers, or [PartialCompiler](https://github.com/anatoliykmetyuk/free-acp/blob/0932ccde36b0efa83dd01b25ca1fee393154d987/core/src/main/scala/freeacp/Language.scala#L32)s are of type `Compiler[F] => LanguageT ~> OptionK[F, ?]` (and `Compiler[F]` is `LanguageT ~> F`). This means that we are able to use the "large" compiler from the "small" ones, so that we can invoke it recursively.

## Conclusion
FreeACP is still a work in progress. The theory and architecture are far from perfect and hopefully will endure substantial changes in future.
