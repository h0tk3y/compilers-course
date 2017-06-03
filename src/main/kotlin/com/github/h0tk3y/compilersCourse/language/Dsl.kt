package com.github.h0tk3y.compilersCourse.language

operator fun Expression.plus(other: Expression) = BinaryOperation(this, other, Plus)
operator fun Expression.minus(other: Expression) = BinaryOperation(this, other, Minus)
operator fun Expression.times(other: Expression) = BinaryOperation(this, other, Times)
operator fun Expression.div(other: Expression) = BinaryOperation(this, other, Div)
operator fun Expression.rem(other: Expression) = BinaryOperation(this, other, Rem)
infix fun Expression.and(other: Expression) = BinaryOperation(this, other, And)
infix fun Expression.or(other: Expression) = BinaryOperation(this, other, Or)
infix fun Expression.eq(other: Expression) = BinaryOperation(this, other, Eq)
infix fun Expression.neq(other: Expression) = BinaryOperation(this, other, Neq)
infix fun Expression.lessThan(other: Expression) = BinaryOperation(this, other, Lt)
infix fun Expression.greaterThan(other: Expression) = BinaryOperation(this, other, Gt)

val Read = FunctionCall(Intrinsic.READ, emptyList())

fun Write(e: Expression) = FunctionCallStatement(FunctionCall(Intrinsic.WRITE, listOf(e)))