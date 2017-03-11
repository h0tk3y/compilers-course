package com.github.h0tk3y.compilersCourse.stack

import com.github.h0tk3y.compilersCourse.Compiler
import com.github.h0tk3y.compilersCourse.language.*

class StatementToStackCompiler : Compiler<Statement, List<StackStatement>> {
    override fun compile(source: Statement): List<StackStatement> {
        val program = arrayListOf<StackStatement?>()
        fun emit(stackStatement: StackStatement) = program.add(stackStatement)

        fun compileExpression(expression: Expression): Unit {
            when (expression) {
                is Const -> emit(Push(expression))
                is Variable -> emit(Ld(expression))
                is UnaryOperation -> {
                    compileExpression(expression.operand)
                    emit(Unop(expression.kind))
                }
                is BinaryOperation -> {
                    compileExpression(expression.left)
                    compileExpression(expression.right)
                    emit(Binop(expression.kind))
                }
            }
        }

        fun emitPlaceholder(): Int {
            program.add(null)
            return program.lastIndex
        }

        fun fillPlaceholder(i: Int, stackStatement: StackStatement) {
            program[i] = stackStatement
        }

        fun compileStatement(statement: Statement) {
            when (statement) {
                Skip -> emit(Nop)
                is Assign -> {
                    compileExpression(statement.expression)
                    emit(St(statement.variable))
                }
                is Read -> {
                    emit(Rd)
                    emit(St(statement.variable))
                }
                is Write -> {
                    compileExpression(statement.expression)
                    emit(Wr)
                }
                is If -> {
                    compileExpression(statement.condition)
                    val pJumpIfNot = emitPlaceholder()
                    compileStatement(statement.trueBranch)
                    val pJumpAfterFalse = emitPlaceholder()
                    fillPlaceholder(pJumpIfNot, Jz(program.lastIndex + 1))
                    compileStatement(statement.falseBranch)
                    fillPlaceholder(pJumpAfterFalse, Jmp(program.lastIndex + 1))
                }
                is While -> {
                    val expressionLabel = program.lastIndex + 1
                    compileExpression(statement.condition)
                    val pJumpOutside = emitPlaceholder()
                    compileStatement(statement.body)
                    emit(Jmp(expressionLabel))
                    fillPlaceholder(pJumpOutside, Jz(program.lastIndex + 1))
                }
                is Chain -> {
                    compileStatement(statement.leftPart)
                    compileStatement(statement.rightPart)
                }
            }
        }

        compileStatement(source)
        return program.map { it!! }
    }
}