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

open class FunctionDeclaration(open val name: String, val parameters: List<Variable>, open val body: Statement)

data class UnresolvedFunction(override val name: String, val dimensions: Int) : FunctionDeclaration(name, (1..dimensions).map { Variable("unresolved") }, Skip) {
    override val body get() = throw IllegalStateException("Getting body of an unresolved function $this")
}

sealed class Intrinsic(name: String, parameters: List<Variable>) : FunctionDeclaration(name, parameters, Skip) {
    override val body: Statement get() = throw IllegalStateException("Getting body of an unresolved function $this")

    object READ : Intrinsic("read", emptyList())
    object WRITE : Intrinsic("write", listOf(Variable("expression")))

    companion object {
        val declarations = listOf(READ, WRITE)
    }
}