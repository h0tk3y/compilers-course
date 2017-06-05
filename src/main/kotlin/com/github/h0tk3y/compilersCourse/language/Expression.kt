package com.github.h0tk3y.compilersCourse.language

sealed class Expression

data class Const(val value: Int) : Expression()
data class StringLiteral(val value: String) : Expression()
data class Variable(val name: String) : Expression()

data class FunctionCall(val functionDeclaration: FunctionDeclaration,
                        val argumentExpressions: List<Expression>) : Expression() {
    init { require(argumentExpressions.size == functionDeclaration.parameters.size) }
}

data class UnaryOperation(val operand: Expression, val kind: UnaryOperationKind) : Expression()
sealed class UnaryOperationKind
object Not : UnaryOperationKind()

fun UnaryOperationKind.semantics(x: Int) = when (this) {
    Not -> if (x == 0) 1 else 0
}

data class BinaryOperation(val left: Expression, val right: Expression, val kind: BinaryOperationKind) : Expression()
sealed class BinaryOperationKind
object Plus : BinaryOperationKind()
object Minus : BinaryOperationKind()
object Times : BinaryOperationKind()
object Div : BinaryOperationKind()
object Rem : BinaryOperationKind()
object And : BinaryOperationKind()
object Or : BinaryOperationKind()
object Eq : BinaryOperationKind()
object Neq : BinaryOperationKind()
object Gt : BinaryOperationKind()
object Lt : BinaryOperationKind()
object Leq : BinaryOperationKind()
object Geq : BinaryOperationKind()

fun BinaryOperationKind.semantics(l: Int, r: Int) = when (this) {
    Plus -> l + r
    Minus -> l - r
    Times -> l * r
    Div -> l / r
    Rem -> l % r
    And -> if (l != 0 && r != 0) 1 else 0
    Or -> if (l != 0 || r != 0) 1 else 0
    Eq -> if (l == r) 1 else 0
    Neq -> if (l != r) 1 else 0
    Gt -> if (l > r) 1 else 0
    Lt -> if (l < r) 1 else 0
    Leq -> if (l <= r) 1 else 0
    Geq -> if (l >= r) 1 else 0
}