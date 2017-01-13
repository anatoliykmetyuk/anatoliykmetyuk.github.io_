---
layout: post
title: Rewriting Process Algebra, Part 2&#58; Engine Theory
categories:
- blog
---
This is a second part of a progress report on my attempt to model a process algebra as an expression rewriting machine. This part covers the theoretical foundations of the two implementations (engines) of SubScript: the [standard one](TODO), and [FreeACP](TODO), the one I am building. The other two parts:

- [Rewriting Process Algebra, Part 1: Introduction to Process Algebra](TODO)
- [Rewriting Process Algebra, Part 3: FreeACP Implementation](TODO)

# Standard SubScript implementation

The standard [implementation](https://github.com/scala-subscript/subscript) of SubScript in Scala takes the *coordinating* approach to the problem with the help of the actor model.

Every process algebra entity, such as operator or atomic action, is represented by its own node (actor) in an execution graph (actor hierarchy). Operators become actors that supervise other actors - operands of these operators. The lower-level actors report all the events that pertain to them to their supervisors. Based on the events, the supervisors coordinate the execution of their subordinates.

These reported events include:

- An atomic action being successfully completed in one of the operator's subordinates.
- An operator itself has completed successfully, according to the definition of the particular operator.
- Some of the operands of an operator requested a break of this operator's execution (behaves roughly the same as a brake of a loop in Java).

## Example
Let us have a look at how our GUI example from [Part 1](TODO) of this series would have been executed in the standard implementation:

1. A hierarchy of actors (nodes) is created: <img src="/media/rewriting-process-algebra-part-2-engine-theory/SubScriptActors.svg" alt="Diagram" width="650" onclick="window.open(this.src)" onmouseover="this.style.cursor='pointer'"/>

2. When the button `first` is pressed, it sends a message to its supervising actor, `*`, which in turn forwards it to `+`. Both these actors have the information that `first` has successfully finished at this point.
3. `+` acts upon this information by cancelling its second operand
4. `*` acts upon this information by instantiating an actor corresponding to its second operand and sending a message to it, ordering it to start execution

## Problem
However, actors and communication between them are hard to explore in mathematical fashion. This is why, the motivation for an alternative implementation of SubScript, FreeACP, is to define an engine for SubScript in mathematical terms rather than engineering ones.

# Rewriting engine implementation
While the standard implementation of SubScript uses the coordinating approach discussed above, FreeACP uses the **rewriting** approach. Its idea is to rewrite SubScript expressions according to the axioms of this algebra, gradually simplifying them. Eventually, every expression is reduced to either ε or δ.

In the process of rewritings, the AAs are **evaluated** (executed) on need. An evaluation of AA is a process of execution of whatever instructions it caries, getting `ε` or `δ` as a result (depending on whether this execution was successful) and substitution of that result in place of that AA.

## Example
Let us see how our GUI example behaves under the rewriting approach.

First, we need to define the axioms we are working under (note that the axioms below are ad-hoc ones, for the sake of this example only):

<img src="/media/rewriting-process-algebra-part-2-engine-theory/axioms-1.gif" alt="Diagram" onclick="window.open(this.src)" onmouseover="this.style.cursor='pointer'"/>

The axioms with the horizontal bar are defined precisely in the "Axioms" section. Roughly, they dictate to evaluate the AAs below the bar, and, depending on the result of this evaluation, rewrite the expression as described by the right hand side expression of the arrow.

Under `(1)` axiom, we need to evaluate the first AAs of both processes, see which one has completed first and rewrite the expression according to the axiom

Assume `button(first)` was completed first. By `(1)`, the expression will be rewritten to `setText(textField, "Hello World")`, which by `(3)` is rewritten to `setText(textField, "Hello World") * ε`. By `(2)`, `setText(textField, "Hello World")` will be evaluated. In the process of evaluation, a side effect of setting the `textField`'s text to `"Hello World"`. Assuming the action completed successfully and hence evaluated to `ε`, by `(2)` the expression will rewrite to `ε * ε`, which by `(3)` is just `ε`. This way, we computed the result of the original PA expression to be `ε`.

## Suspended computations
Some AAs can take time to evaluate. For example, in case of `button(first)`, a reasonable implementation of this AA will wait until the user has pressed `first` and will be considered successful (evaluates to ε) when this wait has completed. But it takes time for a user to press the button. Hence, the evaluation of such an AA can also take time. However, it is often necessary to know the result of such an evaluation to proceed with the rewritings. If the evaluation of AA is not immediate, we shall call it a **suspended computation**.

Functional Programming offers a standard way of treatment for such a scenario: If the result of the computation is of type `A`, it is wrapped into a suspension type and becomes `S[A]`. If `S` is a functor, we can map `S[A]` and apply the rewriting rule within the map function as if we already knew the result `A`. After the mapping, we get an `S[Tree]`, where `Tree` is an expression this one should be rewritten to once `A` is available.

## Axioms
Hence, there are two kinds of axioms: rewriting axioms and suspension axioms. The latter are used in situations when we need to wait for some potentially long-running computation to finish to do the rewriting; the former are for situations when this is not the case.

For example, this is a set of axioms for the sequential composition of processes:

<img src="/media/rewriting-process-algebra-part-2-engine-theory/axioms-2.gif" alt="Diagram" onclick="window.open(this.src)" onmouseover="this.style.cursor='pointer'"/>

A sequence is presented as `[*](list)`, where `list` is a list of expressions, and `::` is concatenation.

The rewriting axioms are of the form `a = b`.

The suspension axioms work on the trees for which there are some suspended computations to compute before it is possible to apply rewriting axioms. They have a horizontal bar, above which is the pattern with one or more unknown elements that need to be computed. Below the bar, there are instructions on how to map the result of the unknown element execution to the new tree which we will rewrite the current one with.

> Note though that the concept of suspended axioms do not appear in either ACP or SubScript. This piece of theory is under development and deals with the real-world problem that some AAs take time to evaluate. Perhaps a better status for them in their current state would be that of the *rules* rather than *axioms*.

It is possible to simplify expression like `εx` without any "heavy" execution, using a rewriting axiom: empty action `ε` followed by some process `x` is the same as `x`. However, for some atom `a`, we can not simplify `ax` without knowing the outcome of `a`. So a suspension axiom is used here to compute the result `r` of `a`, and the rewriting rule is to substitute `r` in place of `a`.

Speaking in terms of Category Theory, given `a: S[A]` and a tree `a * x` that needs rewriting and this particular suspension axiom can work on, the rewritten tree is `a.map(_ * x)`, of type `S[Tree]`.

## Execution semantics
A process algebra engine which works via expression rewriting invokes the rewriting axioms on the input tree until a suspension axiom is available, in which case the suspension axiom will be invoked. This continues until the tree is simplified to ε - success result, or δ - failure.

Precisely, the algorithm is as follows:

1. If a suspension axiom is applicable, apply it and wait for the resulting tree. Once the resulting tree is available, restart this algorithm recursively with this tree as an input.
2. Otherwise, if the input tree is the terminal element (either `ε` or `δ`), return it.
3. Otherwise, apply a rewrite axiom and recursively feed the result to this algorithm.

# Conclusion
This part covered the theory and motivation for the rewriting-based implementation of SubScript, as well as the theory behind the standard implementation. In the [third part](TODO), we will dive into the code and survey the architectural highlights of the work done so far in this direction.