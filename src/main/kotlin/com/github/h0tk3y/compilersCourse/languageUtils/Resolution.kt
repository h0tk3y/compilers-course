package com.github.h0tk3y.compilersCourse.languageUtils

import com.github.h0tk3y.compilersCourse.language.*

@Suppress("UNCHECKED_CAST")
private class Resolution(val program: Program) {
    val namedFunctions = (program.functionDeclarations + Intrinsic.resolvable)
            .groupBy { it.name }
            .mapValues { (_, v) -> v.associateBy { it.parameters.size } }

    fun resolveCallsInFunction(f: FunctionDeclaration): FunctionDeclaration =
            FunctionDeclaration(f.name, f.parameters, resolveCallsInStatement(f.body))

    private fun findBySignature(name: String, nArgs: Int) = namedFunctions[name]?.let { it[nArgs] }

    private fun <T : Expression> resolveCallsIn(expression: Expression): T = when (expression) {
        is Const -> expression
        is Variable -> expression
        is FunctionCall -> {
            val resolvedArgs = expression.argumentExpressions.map<Expression, Expression> { resolveCallsIn(it) }
            if (expression.functionDeclaration is UnresolvedFunction) {
                val name = expression.functionDeclaration.name
                val nArgs = expression.functionDeclaration.dimensions
                val functionDeclaration = findBySignature(name, nArgs)
                                          ?: throw IllegalStateException("Unresolved function $name, $nArgs arguments.")
                expression.copy(functionDeclaration, resolvedArgs)
            } else {
                expression.copy(argumentExpressions = resolvedArgs)
            }
        }
        is UnaryOperation -> expression.copy(resolveCallsIn(expression.operand))
        is BinaryOperation -> expression.copy(left = resolveCallsIn(expression.left),
                                              right = resolveCallsIn(expression.right))
        is StringLiteral -> expression
        is ArrayLiteral -> expression.copy(initializers = expression.initializers.map { resolveCallsIn<Expression>(it) })
    } as T

    private fun resolveCallsInStatement(s: Statement): Statement = when (s) {
        Skip -> Skip
        is Assign -> s.copy(expression = resolveCallsIn(s.expression))
        is If -> s.copy(resolveCallsIn(s.condition), resolveCallsInStatement(s.trueBranch), resolveCallsInStatement(s.falseBranch))
        is While -> s.copy(resolveCallsIn(s.condition), resolveCallsInStatement(s.body))
        is Chain -> s.copy(resolveCallsInStatement(s.leftPart), resolveCallsInStatement(s.rightPart))
        is Return -> s.copy(resolveCallsIn(s.expression))
        is FunctionCallStatement -> s.copy(resolveCallsIn(s.functionCall))
        is Try -> s.copy(resolveCallsInStatement(s.body),
                         s.catchBranches.map { it.copy(body = resolveCallsInStatement(it.body)) },
                         resolveCallsInStatement(s.finallyStatement))
        is Throw -> s.copy(dataExpression = resolveCallsIn(s.dataExpression))
    }
}

fun resolveCalls(program: Program): Program {
    val resolutionP1 = Resolution(program)
    val mainP1 = resolutionP1.resolveCallsInFunction(program.mainFunction)
    val functionsP1 = program.functionDeclarations.filter { it !== program.mainFunction }
                              .map { resolutionP1.resolveCallsInFunction(it) } + mainP1
    return Program(functionsP1, mainP1)
}
