package com.github.h0tk3y.compilersCourse.jvm

import com.github.h0tk3y.compilersCourse.Compiler
import com.github.h0tk3y.compilersCourse.language.Statement
import com.github.h0tk3y.compilersCourse.stack.StatementToStackCompiler

class StatementToJvmCompiler : Compiler<Statement, ByteArray> {
    val statementToStackCompiler = StatementToStackCompiler()
    val stackToJvmCompiler = StackToJvmCompiler()

    override fun compile(source: Statement): ByteArray {
        val stackProgram = statementToStackCompiler.compile(source)
        val bytecode = stackToJvmCompiler.compile(stackProgram)
        return bytecode
    }
}