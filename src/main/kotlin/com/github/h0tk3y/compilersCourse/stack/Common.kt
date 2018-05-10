package com.github.h0tk3y.compilersCourse.stack

import com.github.h0tk3y.compilersCourse.language.FunctionDeclaration
import com.github.h0tk3y.compilersCourse.language.Intrinsic

fun collectVariables(statements: Iterable<StackStatement>) = statements.flatMap {
    when (it) {
        is Ld -> listOf(it.v)
        is St -> listOf(it.v)
        is Pop -> listOf(poppedUnusedValueVariable)
        is Call -> if (it.function.canThrow) listOf(thrownExceptionVariable) else emptyList()
        is TransEx -> listOf(currentExceptionVariable)
        else -> emptyList()
    }
}.distinct()

val FunctionDeclaration.canThrow: Boolean get() = this !is Intrinsic || this.throws