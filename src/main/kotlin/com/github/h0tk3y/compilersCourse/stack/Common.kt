package com.github.h0tk3y.compilersCourse.stack

fun collectVariables(statements: Iterable<StackStatement>) = statements.flatMap {
    when (it) {
        is Ld -> listOf(it.v)
        is St -> listOf(it.v)
        else -> emptyList()
    }
}.distinct()