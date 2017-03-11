package com.github.h0tk3y.compilersCourse.x86

import com.github.h0tk3y.compilersCourse.Compiler
import com.github.h0tk3y.compilersCourse.language.Statement
import com.github.h0tk3y.compilersCourse.stack.StatementToStackCompiler

class StatementToX86Compiler : Compiler<Statement, String> {
    val statementToStackCompiler = StatementToStackCompiler()
    val stackToX86Compiler = StackToX86Compiler()

    override fun compile(source: Statement): String {
        val stackProgram = statementToStackCompiler.compile(source)
        val x86Assembly = stackToX86Compiler.compile(stackProgram)
        return x86Assembly
    }
}