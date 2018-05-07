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

        val unknownStackContent = listOf<StackStatement>()
        val stackContent = mutableMapOf<Int, List<StackStatement>>(0 to emptyList())

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
        stackContent.clear()
        stackContent[0] = emptyList()

        check(exitHandlersStack.isEmpty())
        val returnHandler = CompilationEnvironment.ExitHandler(
            hasExceptionTypes = listOf(ExceptionType("###all-uncaught")),
            hasFinallyBlock = true)
        exitHandlersStack.push(returnHandler)

        val statementsDebugStack = Stack<Statement>()
        val exprsDebugStack = Stack<Expression>()
        val program = arrayListOf<Triple<Any, Statement?, Expression?>>()

        fun mergeStackContents(instructionNumber: Int, content: List<StackStatement>) {
            stackContent.compute(instructionNumber) { _, existing ->
                if (existing != null && existing !== unknownStackContent) {
                    check(existing.size == content.size) {
                        "Stacks do not match:\n$content\n$existing"
                    }
                }
                content
            }
        }

        fun emit(stackStatement: StackStatement, atIndex: Int = program.size) {
            val currentStack = stackContent[atIndex]!!
            val modifiedStack = when (stackStatement) {
                is Ld, is Push, is PushPooled -> currentStack + stackStatement
                is St, is Jz, Ret1, Pop -> currentStack.dropLast(1)
                is Call -> currentStack.dropLast(stackStatement.function.parameters.size) + stackStatement
                is Unop -> currentStack.dropLast(1) + stackStatement
                is Binop -> currentStack.dropLast(2) + stackStatement
                is Jmp, TransEx, Ret0, Nop -> currentStack
                TransEx -> currentStack
            }
            when (stackStatement) {
                is Jmp -> {
                    mergeStackContents(stackStatement.nextInstruction, modifiedStack)
                    stackContent[atIndex + 1] = unknownStackContent
                }
                is Jz -> {
                    mergeStackContents(stackStatement.nextInstruction, modifiedStack)
                    mergeStackContents(atIndex + 1, modifiedStack)
                }
                else -> mergeStackContents(atIndex + 1, modifiedStack)
            }
            if (atIndex == program.size) {
                val entry = Triple(stackStatement, statementsDebugStack.lastOrNull(), exprsDebugStack.lastOrNull())
                program.add(entry)
            } else {
                val (insn, st, ex) = program[atIndex]
                check(insn is JumpPlaceholder)
                program[atIndex] = Triple(stackStatement, st, ex)
            }
        }

        fun nextInsn() = program.size

        fun emitJumpPlaceholder(fillFunction: (Int) -> StackStatement): JumpPlaceholder {
            val currentStack = stackContent[program.size]!!
            stackContent[program.size + 1] = when (fillFunction) {
                ::Jmp ->  currentStack
                ::Jz -> currentStack.dropLast(1)
                else -> throw UnsupportedOperationException()
            }

            val jumpPlaceholder = JumpPlaceholder(program.lastIndex + 1, fillFunction, RuntimeException())
            val entry = Triple(jumpPlaceholder, statementsDebugStack.lastOrNull(), exprsDebugStack.lastOrNull())
            program.add(entry)
            return jumpPlaceholder
        }

        fun fillJumpPlaceholder(placeholder: JumpPlaceholder, jumpTo: Int) {
            val placeholderEntry = program[placeholder.position]
            check(placeholderEntry.first === placeholder)
            emit(placeholder.fillFunction(jumpTo), placeholder.position)
        }

        var arrayLiteralDepth = 0
        lateinit var compileArrayLiteral: (arrayLiteral: ArrayLiteral) -> Unit

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
            val stackRemainingItemsCount = stackContent[program.size]!!.size
            repeat(stackRemainingItemsCount) { // Remove the expression parts from the stack
                emit(Pop)
            }
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
                is ArrayLiteral -> compileArrayLiteral(expression)
            }.exhaustive
            check(exprsDebugStack.pop() == expression)
        }

        compileArrayLiteral = { arrayLiteral ->
            arrayLiteralDepth++
            val arrayVariable = Variable("###array-under-construction-$arrayLiteralDepth")
            emit(Push(Const(arrayLiteral.initializers.size)))
            emit(Push(Const(0)))
            val intrinsic = if (arrayLiteral.isBoxed) Intrinsic.ARRMAKEBOX else Intrinsic.ARRMAKE
            emit(Call(intrinsic))
            emit(St(arrayVariable))
            for ((index, init) in arrayLiteral.initializers.withIndex()) {
                emit(Ld(arrayVariable))
                emit(Push(Const(index)))
                compileExpression(init)
                emit(Call(Intrinsic.ARRSET))
                emit(Pop)
            }
            emit(Ld(arrayVariable))
            arrayLiteralDepth--
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