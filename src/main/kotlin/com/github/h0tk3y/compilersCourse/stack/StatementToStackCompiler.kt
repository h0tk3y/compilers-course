package com.github.h0tk3y.compilersCourse.stack

import com.github.h0tk3y.compilersCourse.Compiler
import com.github.h0tk3y.compilersCourse.exhaustive
import com.github.h0tk3y.compilersCourse.language.*

class StatementToStackCompiler : Compiler<Program, StackProgram> {
    override fun compile(source: Program): StackProgram {
        val functionBodies = source.functionDeclarations.associate { it to compileFunction(it.body) }
        return StackProgram(functionBodies, source.mainFunction)
    }

    private fun compileFunction(source: Statement): List<StackStatement> {
        val program = arrayListOf<StackStatement?>()
        fun emit(stackStatement: StackStatement) = program.add(stackStatement).run { }

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
                is FunctionCall -> {
                    for (e in expression.argumentExpressions)
                        compileExpression(e)
                    emit(Call(expression.functionDeclaration))
                }
            }.exhaustive
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
                is Return -> {
                    compileExpression(statement.expression)
                    emit(Ret1)
                }
                is FunctionCallStatement -> {
                    compileExpression(statement.functionCall)
                    emit(Pop)
                }
            }.exhaustive
        }

        compileStatement(source)
        return program.map { it!! }
    }
}