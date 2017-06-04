package com.github.h0tk3y.compilersCourse.language

data class Program(val functionDeclarations: List<FunctionDeclaration>,
                   val mainFunction: FunctionDeclaration)