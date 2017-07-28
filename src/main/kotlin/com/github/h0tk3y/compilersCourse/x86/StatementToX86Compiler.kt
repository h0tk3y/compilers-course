package com.github.h0tk3y.compilersCourse.x86

import com.github.h0tk3y.compilersCourse.Compiler
import com.github.h0tk3y.compilersCourse.language.Program
import com.github.h0tk3y.compilersCourse.stack.StatementToStackCompiler

class StatementToX86Compiler(val targetPlatform: TargetPlatform) : Compiler<Program, String> {
    val statementToStackCompiler = StatementToStackCompiler()
    val stackToX86Compiler = StackToX86Compiler(targetPlatform)

    override fun compile(source: Program): String {
        val stackProgram = statementToStackCompiler.compile(source)
        val x86Assembly = stackToX86Compiler.compile(stackProgram)
        return x86Assembly
    }
}