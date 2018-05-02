package com.github.h0tk3y.compilersCourse.language

sealed class Statement

object Skip : Statement()
data class Assign(val variable: Variable, val expression: Expression) : Statement()
data class If(val condition: Expression,
              val trueBranch: Statement,
              val falseBranch: Statement) : Statement()

data class While(val condition: Expression,
                 val body: Statement) : Statement()

data class Chain(val leftPart: Statement,
                 val rightPart: Statement) : Statement()

data class Return(val expression: Expression) : Statement()

data class FunctionCallStatement(val functionCall: FunctionCall) : Statement()

fun chainOf(vararg statements: Statement) = statements.reduce(::Chain)

// Exceptions

data class ExceptionType(val name: String)

data class CatchBranch(val exceptionType: ExceptionType, val dataVariable: Variable, val body: Statement)

data class Try(
    val body: Statement,
    val catchBranches: List<CatchBranch>,
    val finallyStatement: Statement = Skip
) : Statement()

data class Throw(val exceptionType: ExceptionType, val dataExpression: Expression) : Statement()