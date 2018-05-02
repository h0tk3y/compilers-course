package com.github.h0tk3y.compilersCourse.stack

import com.github.h0tk3y.compilersCourse.Compiler
import com.github.h0tk3y.compilersCourse.exhaustive
import com.github.h0tk3y.compilersCourse.language.*
import com.github.h0tk3y.compilersCourse.languageUtils.LanguageVisitor
import java.util.*

class StatementToStackCompiler : Compiler<Program, StackProgram> {
    data class JumpPlaceholder(
        val position: Int,
        val fillFunction: (Int) -> StackStatement,
        val traceException: Exception = RuntimeException()
    )

    class CompilationEnvironment(val exceptionIds: Map<ExceptionType, Int>) {
        val stringPool = mutableListOf<CharArray>()

        fun saveStringToPool(stringLiteral: StringLiteral): Int {
            val charArray = stringLiteral.value.toCharArray()
            return stringPool.withIndex().firstOrNull { (_, v) -> v.contentEquals(charArray) }?.index ?: run {
                val result = stringPool.size
                stringPool.add(stringLiteral.value.toCharArray())
                result
            }
        }

        class ExitHandler(
            val hasExceptionTypes: List<ExceptionType>,
            val hasFinallyBlock: Boolean
        ) {
            val throwPlaceholders = mutableListOf<JumpPlaceholder>()
            val exitWithoutCatchPlaceholders = mutableListOf<JumpPlaceholder>()
        }

        val exitHandlersStack = Stack<ExitHandler>().apply { }

        fun exitHandlerWhenThrown() =
            exitHandlersStack.last { it.hasFinallyBlock || it.hasExceptionTypes.isNotEmpty() }
    }

    override fun compile(source: Program): StackProgram {
        val exceptions = generateExceptionIds(collectExceptions(source.functionDeclarations.map { it.body }))
        val environment = CompilationEnvironment(exceptions)
        val functionBodies = source.functionDeclarations.associate { it to environment.compileFunction(it.body) }
        return StackProgram(functionBodies, source.mainFunction, environment.stringPool)
    }

    private fun CompilationEnvironment.compileFunction(source: Statement): List<StackStatement> {
        check(exitHandlersStack.isEmpty())
        val returnHandler = CompilationEnvironment.ExitHandler(
            hasExceptionTypes = listOf(ExceptionType("###all-uncaught")),
            hasFinallyBlock = true)
        exitHandlersStack.push(returnHandler)

        val statementsDebugStack = Stack<Statement>()
        val exprsDebugStack = Stack<Expression>()
        val program = arrayListOf<Triple<Any, Statement?, Expression?>>()

        fun emit(stackStatement: StackStatement) {
            val entry = Triple(stackStatement, statementsDebugStack.lastOrNull(), exprsDebugStack.lastOrNull())
            program.add(entry)
        }

        fun nextInsn() = program.size

        fun emitJumpPlaceholder(fillFunction: (Int) -> StackStatement): JumpPlaceholder {
            val jumpPlaceholder = JumpPlaceholder(program.lastIndex + 1, fillFunction, RuntimeException())
            val entry = Triple(jumpPlaceholder, statementsDebugStack.lastOrNull(), exprsDebugStack.lastOrNull())
            program.add(entry)
            return jumpPlaceholder
        }

        fun fillJumpPlaceholder(placeholder: JumpPlaceholder, jumpTo: Int) {
            val placeholderEntry = program[placeholder.position]
            check(placeholderEntry.first === placeholder)
            program[placeholder.position] = placeholderEntry.copy(first = placeholder.fillFunction(jumpTo))
        }

        fun emitThrownExceptionHandling() {
            emit(Ld(thrownExceptionVariable))
            emit(Push(Const(0)))
            emit(Binop(Neq))
            val jzWhenNotThrown = emitJumpPlaceholder(::Jz)
            emit(St(exceptionDataVariable))
            emit(Ld(thrownExceptionVariable))
            emit(St(currentExceptionVariable))
            emit(Push(Const(0)))
            emit(St(thrownExceptionVariable))
            val jmpIfThrown = emitJumpPlaceholder(::Jmp)
            exitHandlerWhenThrown().throwPlaceholders.add(jmpIfThrown)
            fillJumpPlaceholder(jzWhenNotThrown, nextInsn())
        }

        fun compileExpression(expression: Expression) {
            exprsDebugStack.push(expression)
            when (expression) {
                is Const -> emit(Push(expression))
                is StringLiteral -> {
                    val stringIndex = saveStringToPool(expression)
                    emit(PushPooled(stringIndex))
                    emit(Call(Intrinsic.STRDUP))
                }
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
                    if (expression.functionDeclaration.canThrow) {
                        emitThrownExceptionHandling()
                    }
                    Unit
                }
            }.exhaustive
            check(exprsDebugStack.pop() == expression)
        }

        fun compileStatement(statement: Statement) {
            statementsDebugStack.push(statement)
            @Suppress("IMPLICIT_CAST_TO_ANY")
            when (statement) {
                Skip -> emit(Nop)
                is Assign -> {
                    compileExpression(statement.expression)
                    emit(St(statement.variable))
                }
                is If -> {
                    compileExpression(statement.condition)
                    val pJumpIfNot = emitJumpPlaceholder(::Jz)
                    compileStatement(statement.trueBranch)
                    val pJumpOverFalseBranch =
                        if (statement.falseBranch != Skip)
                            emitJumpPlaceholder(::Jmp) else
                            JumpPlaceholder(-1, ::Jmp, RuntimeException())
                    fillJumpPlaceholder(pJumpIfNot, nextInsn())
                    compileStatement(statement.falseBranch)
                    if (statement.falseBranch != Skip)
                        fillJumpPlaceholder(pJumpOverFalseBranch, nextInsn())
                    Unit
                }
                is While -> {
                    val expressionLabel = nextInsn()
                    compileExpression(statement.condition)
                    val pJumpOutside = emitJumpPlaceholder(::Jz)
                    compileStatement(statement.body)
                    emit(Jmp(expressionLabel))
                    fillJumpPlaceholder(pJumpOutside, nextInsn())
                }
                is Chain -> {
                    compileStatement(statement.leftPart)
                    compileStatement(statement.rightPart)
                }
                is Return -> {
                    compileStatement(Throw(returnNormallyFakeException, statement.expression))
                }
                is FunctionCallStatement -> {
                    compileExpression(statement.functionCall)
                    emit(Pop)
                }
                is Try -> {
                    val tryExitHandler = CompilationEnvironment.ExitHandler(
                        hasExceptionTypes = statement.catchBranches.map { it.exceptionType },
                        hasFinallyBlock = statement.finallyStatement != Skip)

                    exitHandlersStack.push(tryExitHandler)

                    // Inside the statement body, the throwing & returning instruction positions are recorded into the
                    // exit handler.
                    compileStatement(statement.body)
                    // If the body finishes normally, we jump over the catch blocks (into the finally block, if present)
                    val exitNormallyPlaceholder = emitJumpPlaceholder(::Jmp)
                    tryExitHandler.exitWithoutCatchPlaceholders.add(exitNormallyPlaceholder)
                    tryExitHandler.throwPlaceholders.forEach { fillJumpPlaceholder(it, nextInsn()) }

                    var jzToNextBranchPlaceholder: JumpPlaceholder? = null // Jump over a catch block if exception type does not match
                    val catchExitHandlers = statement.catchBranches.map { branch ->
                        if (jzToNextBranchPlaceholder != null) {
                            fillJumpPlaceholder(jzToNextBranchPlaceholder!!, nextInsn())
                        }
                        val catchExitHandler = CompilationEnvironment.ExitHandler(
                            listOf(ExceptionType("###all-uncaught")), statement.finallyStatement != Skip
                        )
                        emit(Ld(currentExceptionVariable))
                        emit(Push(Const(exceptionIds[branch.exceptionType]!!)))
                        emit(Binop(Eq))
                        jzToNextBranchPlaceholder = emitJumpPlaceholder(::Jz)

                        exitHandlersStack.push(catchExitHandler)
                        compileStatement(Assign(branch.dataVariable, exceptionDataVariable))
                        compileStatement(branch.body)
                        check(exitHandlersStack.pop() == catchExitHandler)

                        compileStatement(Assign(currentExceptionVariable, Const(0)))
                        compileStatement(Assign(exceptionDataVariable, Const(0)))
                        val jumpOverOtherCatchBranchesWhenCaught = emitJumpPlaceholder(::Jmp)
                        tryExitHandler.exitWithoutCatchPlaceholders.add(jumpOverOtherCatchBranchesWhenCaught)

                        catchExitHandler
                    }

                    if (jzToNextBranchPlaceholder != null) {
                        fillJumpPlaceholder(jzToNextBranchPlaceholder!!, nextInsn())
                    }

                    (tryExitHandler.exitWithoutCatchPlaceholders +
                     catchExitHandlers.flatMap { it.throwPlaceholders + it.exitWithoutCatchPlaceholders })
                        .forEach { fillJumpPlaceholder(it, nextInsn()) }

                    val finallyExitHandler = CompilationEnvironment.ExitHandler(emptyList(), true)
                    exitHandlersStack.push(finallyExitHandler)
                    compileStatement(statement.finallyStatement)

                    val jmpOutsideOnUncaughtException = emitJumpPlaceholder(::Jmp)
                    exitHandlersStack.last { it.throwPlaceholders.add(jmpOutsideOnUncaughtException) }

                    check(exitHandlersStack.pop() == finallyExitHandler)
                    (finallyExitHandler.throwPlaceholders + finallyExitHandler.exitWithoutCatchPlaceholders).forEach {
                        fillJumpPlaceholder(it, nextInsn())
                    }
                    // nextInsn should handle uncaught exceptions
                    exitHandlersStack.pop().also { check(it === tryExitHandler) }

                    emit(Ld(currentExceptionVariable))
                    emit(Push(Const(0)))
                    emit(Binop(Eq))
                    val jzOnUncaughtExceptionPlaceholder = emitJumpPlaceholder(::Jz)
                    exitHandlerWhenThrown().throwPlaceholders.add(jzOnUncaughtExceptionPlaceholder)
                }
                is Throw -> {
                    emit(Push(Const(exceptionIds[statement.exceptionType]!!)))
                    emit(St(currentExceptionVariable))
                    compileExpression(statement.dataExpression)
                    emit(St(exceptionDataVariable))
                    val exitPlaceholder = emitJumpPlaceholder(::Jmp)
                    exitHandlerWhenThrown().throwPlaceholders.add(exitPlaceholder)
                }
            }.exhaustive
            check(statementsDebugStack.pop() == statement)
        }

        compileStatement(source)
        // Implicit zero return value
        emit(Push(Const(0)))
        emit(St(exceptionDataVariable))

        check(exitHandlersStack.peek() == returnHandler)
        exitHandlersStack.pop()
        (returnHandler.throwPlaceholders + returnHandler.exitWithoutCatchPlaceholders).forEach {
            fillJumpPlaceholder(it, nextInsn())
        }

        emit(TransEx)
        emit(Ld(exceptionDataVariable))
        emit(Ret1)

        return program.mapIndexed { idx, (insn, st, ex) ->
            if (insn is StackStatement) {
                insn
            } else {
                val forStString = st?.let { " at statement $it" }.orEmpty()
                val forExString = ex?.let { " at expression $it" }.orEmpty()
                throw IllegalStateException("Empty placeholder at $idx$forStString$forExString",
                                            (insn as JumpPlaceholder).traceException)
            }
        }
    }
}

private fun collectExceptions(statements: List<Statement>): Set<ExceptionType> {
    val exceptionsCollector = object : LanguageVisitor() {
        val exceptions = mutableSetOf<ExceptionType>()

        override fun visitExceptionType(exceptionType: ExceptionType) {
            exceptions += exceptionType
        }
    }

    statements.forEach { exceptionsCollector.visitStatement(it) }
    return exceptionsCollector.exceptions
}

fun generateExceptionIds(exceptions: Iterable<ExceptionType>): Map<ExceptionType, Int> {
    return exceptions.mapIndexed { index, exceptionType -> exceptionType to index + 1 }.toMap() +
           (returnNormallyFakeException to returnNormallyFakeExceptionId)
}