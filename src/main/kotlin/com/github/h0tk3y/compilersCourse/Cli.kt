package com.github.h0tk3y.compilersCourse

import com.github.h0tk3y.betterParse.parser.ErrorResult
import com.github.h0tk3y.betterParse.parser.Parsed
import com.github.h0tk3y.compilersCourse.language.NaiveInterpreter
import com.github.h0tk3y.compilersCourse.language.Program
import com.github.h0tk3y.compilersCourse.languageUtils.resolveCalls
import com.github.h0tk3y.compilersCourse.parsing.ProgramGrammar
import com.github.h0tk3y.compilersCourse.stack.NaiveStackInterpreter
import com.github.h0tk3y.compilersCourse.stack.StatementToStackCompiler
import com.github.h0tk3y.compilersCourse.x86.StatementToX86Compiler
import com.github.h0tk3y.compilersCourse.x86.TargetPlatform
import com.github.h0tk3y.compilersCourse.x86.assemble
import com.github.h0tk3y.compilersCourse.x86.determinePlatform
import java.io.File
import kotlin.system.exitProcess

object Cli {
    @JvmStatic
    fun main(args: Array<String>) {
        val programFile = args.getOrNull(1)?.let(::File)?.takeIf(File::isFile) ?: printUsageAndExit()
        val program = parse(programFile)?.let(::resolveCalls) ?: exitProcess(2)
        when (args.getOrNull(0) ?: printUsageAndExit()) {
            "-i" -> {
                println("Enter space-separated program inputs:")
                val inputs = readLine()!!.split("\\s+".toRegex()).map(String::toInt)
                val interpreter = NaiveInterpreter()
                val outputs = interpreter.run(program.mainFunction.body, inputs).output
                println(outputs.joinToString("\n") { it?.toString() ?: ">" })
            }
            "-s" -> {
                println("Enter space-separated program inputs:")
                val inputs = readLine()!!.split("\\s+".toRegex()).map(String::toInt)
                val stackCompiler = StatementToStackCompiler()
                val stackProgram = stackCompiler.compile(program)
                val stackInterpreter = NaiveStackInterpreter()
                val outputs = stackInterpreter.run(stackProgram, inputs).output
                println(outputs.joinToString("\n") { it?.toString() ?: ">" })
            }
            "-o" -> {
                val platform = determinePlatform()
                val x86Compiler = StatementToX86Compiler(platform)
                val asm = x86Compiler.compile(program)
                val exe = assemble(
                    asm,
                    parentDir = programFile.parentFile,
                    programName = programFile.nameWithoutExtension.let {
                        if (platform == TargetPlatform.WIN) it + ".exe" else it
                    }
                )
                println(exe)
            }
            else -> printUsageAndExit()
        }
    }

    private fun printUsageAndExit(): Nothing {
        println("""
            Usage: rc (-i|-s|-o) filename.expr
                -i: Interpret the program
                -s: Compile the program to the stack machine internal language and interpret
                -o: Compile the program to i386
                filename.expr: Existing program file
            """.trimIndent())
        exitProcess(1)
    }

    private fun parse(file: File): Program? {
        val result = ProgramGrammar.tryParse(ProgramGrammar.tokenizer.tokenize(file.inputStream()))
        when (result) {
            is Parsed -> return result.value
            is ErrorResult -> {
                println("Could not parse $file: ${result}"); return null
            }
        }
    }

    private fun compileToI386(program: Program, targetFile: File) {

    }
}