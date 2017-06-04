import com.github.h0tk3y.compilersCourse.language.NaiveProgramInterpreter
import com.github.h0tk3y.compilersCourse.run
import com.github.h0tk3y.compilersCourse.stack.NaiveStackInterpreter
import com.github.h0tk3y.compilersCourse.stack.StatementToStackCompiler
import com.github.h0tk3y.compilersCourse.x86.StatementToX86Compiler
import com.github.h0tk3y.compilersCourse.x86.TargetPlatform
import java.io.File
import java.nio.file.Files

abstract class TestCaseRunner {
    abstract fun runTestCase(testCase: TestCase)
}

class InterpreterRunner : TestCaseRunner() {
    val interpreter = NaiveProgramInterpreter()

    override fun runTestCase(testCase: TestCase) {
        val output = interpreter.run(testCase.program, testCase.input).output
        testCase.checkOutput(output)
    }
}

class StackRunner : TestCaseRunner() {
    val compiler = StatementToStackCompiler()
    val interpreter = NaiveStackInterpreter()

    override fun runTestCase(testCase: TestCase) {
        val stackProgram = compiler.compile(testCase.program)
        val stackMachine = interpreter.run(stackProgram, testCase.input)
        testCase.checkOutput(stackMachine.output)
    }
}

class X86Runner : TestCaseRunner() {
    val compiler = StatementToX86Compiler(TargetPlatform.WIN)

    val asmCache = linkedMapOf<String, File>()

    fun assemble(asm: String): File = asmCache.computeIfAbsent(asm) {
        val tmpDir = Files.createTempDirectory(null).toFile()
        val asmFile = File(tmpDir, "asm.s").apply { writeText(asm) }
        val exeFile = File(tmpDir, "output.exe")
        val cmd = arrayOf("gcc", "-m32", "C:/ubuntu-usr/intrinsics-win.o", asmFile.absolutePath,
                          "-o", exeFile.absolutePath)
        val exec = Runtime.getRuntime().exec(cmd)
        exec.waitFor()
        val gccOutput = exec.inputStream.reader().forEachLine(::println)
        val gccErr = exec.errorStream.reader().forEachLine(::println)
        val gccResult = exec.exitValue()
        exeFile
    }

    fun runExe(exe: File, input: List<String>): List<String> {
        val cmd = arrayOf(exe.absolutePath)
        val exec = Runtime.getRuntime().exec(cmd)
        val stdin = exec.outputStream.writer()
        for (i in input) {
            stdin.write("$i\r\n")
        }
        stdin.close()
        val result = exec.inputStream.reader().readLines()
        exec.waitFor()
        return result
    }

    override fun runTestCase(testCase: TestCase) {
        val output = compiler.compile(testCase.program)
        val exe = assemble(output)
        val out = runExe(exe, testCase.input.map { "$it" })
        testCase.checkOutput(out.flatMap{ it.split(" ").map { it.toIntOrNull() } })
    }
}