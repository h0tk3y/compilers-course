package com.github.h0tk3y.compilersCourse

interface Compiler<TSource, TTarget> {
    fun compile(source: TSource): TTarget
}