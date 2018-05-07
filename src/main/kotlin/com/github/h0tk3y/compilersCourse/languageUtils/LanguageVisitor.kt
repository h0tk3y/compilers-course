package com.github.h0tk3y.compilersCourse.languageUtils

import com.github.h0tk3y.compilersCourse.language.*

open class LanguageVisitor {
    open fun visitStatement(statement: Statement): Any =
            when (statement) {
                Skip -> visitSkip(statement as Skip)
                is Assign -> visitAssign(statement)
                is If -> visitIf(statement)
                is While -> visitWhile(statement)
                is Chain -> visitChain(statement)
                is Return -> visitReturn(statement)
                is FunctionCallStatement -> visitFunctionCallStatement(statement)
                is Try -> visitTry(statement)
                is Throw -> visitThrow(statement)
            }

    open fun visitFunctionCallStatement(functionCallStatement: FunctionCallStatement): Any =
            visitFunctionCall(functionCallStatement.functionCall)

    open fun visitReturn(returns: Return): Any =
            visitExpression(returns.expression)

    open fun visitSkip(skip: Skip): Any = Unit

    open fun visitChain(statement: Chain): Any {
        visitStatement(statement.leftPart)
        visitStatement(statement.rightPart)
        return Unit
    }

    open fun visitWhile(statement: While): Any {
        visitExpression(statement.condition)
        visitStatement(statement.body)
        return Unit
    }

    open fun visitIf(statement: If): Any {
        visitExpression(statement.condition)
        visitStatement(statement.trueBranch)
        visitStatement(statement.falseBranch)
        return Unit
    }

    open fun visitAssign(assign: Assign): Any {
        visitVariable(assign.variable)
        visitExpression(assign.expression)
        return Unit
    }

    open fun visitTry(tries: Try): Any {
        visitStatement(tries.body)
        tries.catchBranches.forEach {
            visitExceptionType(it.exceptionType)
            visitVariable(it.dataVariable)
            visitStatement(it.body)
        }
        visitStatement(tries.finallyStatement)
        return Unit
    }

    open fun visitThrow(throws: Throw): Any {
        visitExceptionType(throws.exceptionType)
        visitExpression(throws.dataExpression)
        return Unit
    }

    open fun visitExpression(expression: Expression): Any = when (expression) {
        is Const -> visitConst(expression)
        is StringLiteral -> visitStringLiteral(expression)
        is Variable -> visitVariable(expression)
        is FunctionCall -> visitFunctionCall(expression)
        is UnaryOperation -> visitUnaryOperation(expression)
        is BinaryOperation -> visitBinaryOperation(expression)
        is ArrayLiteral -> visitArrayLiteral(expression)
    }

    open fun visitStringLiteral(stringLiteral: StringLiteral): Any = Unit

    open fun visitVariable(variable: Variable): Any = Unit

    open fun visitConst(const: Const): Any = Unit

    open fun visitUnaryOperation(unaryOperation: UnaryOperation): Any {
        visitExpression(unaryOperation.operand)
        return Unit
    }

    open fun visitBinaryOperation(binaryOperation: BinaryOperation): Any {
        visitExpression(binaryOperation.left)
        visitExpression(binaryOperation.right)
        return Unit
    }

    open fun visitFunctionCall(functionCall: FunctionCall): Any {
        functionCall.argumentExpressions.forEach {
            visitExpression(it)
        }
        return Unit
    }

    open fun visitArrayLiteral(arrayLiteral: ArrayLiteral): Any {
        arrayLiteral.initializers.forEach {
            visitExpression(it)
        }
        return Unit
    }

    open fun visitExceptionType(exceptionType: ExceptionType) = Unit
}