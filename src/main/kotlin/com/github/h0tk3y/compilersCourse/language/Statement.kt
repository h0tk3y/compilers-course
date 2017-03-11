package com.github.h0tk3y.compilersCourse.language

sealed class Statement

object Skip : Statement()
class Assign(val variable: Variable, val expression: Expression) : Statement()
class Read(val variable: Variable) : Statement()
class Write(val expression: Expression) : Statement()
class If(val condition: Expression,
         val trueBranch: Statement,
         val falseBranch: Statement) : Statement()
class While(val condition: Expression,
            val body: Statement) : Statement()
class Chain(val leftPart: Statement,
            val rightPart: Statement) : Statement()

fun chainOf(vararg statements: Statement) = statements.reduce(::Chain)