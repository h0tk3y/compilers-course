package com.github.h0tk3y.compilersCourse.stack

import com.github.h0tk3y.compilersCourse.language.BinaryOperationKind
import com.github.h0tk3y.compilersCourse.language.Const
import com.github.h0tk3y.compilersCourse.language.UnaryOperationKind
import com.github.h0tk3y.compilersCourse.language.Variable

sealed class StackStatement

object Nop : StackStatement()
object Rd : StackStatement()
object Wr : StackStatement()
data class Push(val constant: Const) : StackStatement()
data class Ld(val v: Variable) : StackStatement()
data class St(val v: Variable) : StackStatement()
data class Unop(val kind: UnaryOperationKind) : StackStatement()
data class Binop(val kind: BinaryOperationKind) : StackStatement()
data class Jmp(val nextInstruction: Int) : StackStatement()
data class Jz(val nextInstruction: Int) : StackStatement()
