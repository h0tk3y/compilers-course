package com.github.h0tk3y.compilersCourse.stack

import com.github.h0tk3y.compilersCourse.language.*

sealed class StackStatement

object Nop : StackStatement()
object Pop : StackStatement()
data class Push(val constant: Const) : StackStatement()
data class PushPooled(val id: Int) : StackStatement()
data class Ld(val v: Variable) : StackStatement()
data class St(val v: Variable) : StackStatement()
data class Unop(val kind: UnaryOperationKind) : StackStatement()
data class Binop(val kind: BinaryOperationKind) : StackStatement()
data class Jmp(val nextInstruction: Int) : StackStatement()
data class Jz(val nextInstruction: Int) : StackStatement()
data class Call(val function: FunctionDeclaration): StackStatement()
object TransEx : StackStatement()
object Ret1 : StackStatement()
object Ret0 : StackStatement()

data class StackProgram(val functions: Map<FunctionDeclaration, List<StackStatement>>,
                        val entryPoint: FunctionDeclaration,
                        val literalPool: List<CharArray>)

val currentExceptionVariable = Variable("###current-exception")
val thrownExceptionVariable = Variable("###thrown-exception")
val exceptionDataVariable = Variable("###exception-data")
val poppedUnusedValueVariable = Variable("###popped-unused")

val returnNormallyFakeException = ExceptionType("ReturnNormally")
val returnNormallyFakeExceptionId = -1

val StackProgram.code get() =
    functions[entryPoint]!!