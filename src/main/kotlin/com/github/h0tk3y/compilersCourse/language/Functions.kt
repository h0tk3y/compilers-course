package com.github.h0tk3y.compilersCourse.language

import java.util.*

open class FunctionDeclaration(open val name: String, val parameters: List<Variable>, open val body: Statement) {
    override fun hashCode(): Int = Objects.hash(name, parameters)
    override fun equals(other: Any?) =
            other is FunctionDeclaration && other.name == name && other.parameters == parameters

    override fun toString(): String = "$name(${parameters.map { it.name }.joinToString()})"
}

data class UnresolvedFunction(override val name: String, val dimensions: Int) : FunctionDeclaration(name, (1..dimensions).map { Variable("unresolved") }, Skip) {
    override val body get() = throw IllegalStateException("Getting body of an unresolved function $this")
}

sealed class Intrinsic(name: String, parameters: List<Variable>, val throws: Boolean = false) : FunctionDeclaration(name, parameters, Skip) {
    override val body: Statement get() = throw IllegalStateException("Getting body of an unresolved function $this")

    object READ : Intrinsic("read", emptyList())
    object WRITE : Intrinsic("write", listOf(Variable("expression")))
    object STRMAKE : Intrinsic("strmake", listOf(Variable("n"), Variable("c")))
    object STRCMP : Intrinsic("strcmp", listOf(Variable("S1"), Variable("S2")))
    object STRGET : Intrinsic("strget", listOf(Variable("S"), Variable("i")))
    object STRDUP : Intrinsic("strdup", listOf(Variable("S")))
    object STRSET : Intrinsic("strset", listOf(Variable("S"), Variable("i"), Variable("c")))
    object STRCAT : Intrinsic("strcat", listOf(Variable("S1"), Variable("S2")))
    object STRSUB : Intrinsic("strsub", listOf(Variable("S"), Variable("i"), Variable("j")))
    object STRLEN : Intrinsic("strlen", listOf(Variable("S")))

    companion object {
        val resolvable by lazy { listOf(READ, WRITE, STRMAKE, STRCMP, STRGET,
                                        STRDUP, STRSET, STRCAT, STRSUB, STRLEN) }
    }
}