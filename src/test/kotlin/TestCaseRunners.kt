
import com.github.h0tk3y.compilersCourse.language.NaiveProgramInterpreter
import com.github.h0tk3y.compilersCourse.run
import com.github.h0tk3y.compilersCourse.stack.NaiveStackInterpreter
import com.github.h0tk3y.compilersCourse.stack.StatementToStackCompiler
import com.github.h0tk3y.compilersCourse.x86.StatementToX86Compiler
import com.github.h0tk3y.compilersCourse.x86.assemble
import com.github.h0tk3y.compilersCourse.x86.determinePlatform
import com.github.h0tk3y.compilersCourse.x86.runExe
import org.junit.Assert
import org.junit.AssumptionViolatedException
import java.io.File

abstract class TestCaseRunner {
    open fun runTestCase(testCase: TestCase) {
        if (!testCase.canRunOnRunner(this)) {
            throw AssumptionViolatedException("The test case cannot run on this runner ${this@TestCaseRunner}.")
        }
    }
}

class InterpreterRunner : TestCaseRunner() {
    val interpreter = NaiveProgramInterpreter()

    override fun runTestCase(testCase: TestCase) {
        super.runTestCase(testCase)
        val output = interpreter.run(testCase.program, testCase.input).output
        testCase.checkOutput(output)
    }
}

class StackRunner : TestCaseRunner() {
    val compiler = StatementToStackCompiler()
    val interpreter = NaiveStackInterpreter()

    override fun runTestCase(testCase: TestCase) {
        super.runTestCase(testCase)
        val stackProgram = compiler.compile(testCase.program)
        val stackMachine = interpreter.run(stackProgram, testCase.input)
        testCase.checkOutput(stackMachine.output)
    }
}

class X86Runner : TestCaseRunner() {
    val compiler = run {
        StatementToX86Compiler(determinePlatform())
    }

    val asmCache = hashMapOf<String, File>()

    fun assembleCached(asm: String): File = asmCache.computeIfAbsent(asm) { _: String -> assemble(asm) }

    override fun runTestCase(testCase: TestCase) {
        super.runTestCase(testCase)
        val output = compiler.compile(testCase.program)
        val exe = assembleCached(output)
        val out = runExe(exe, testCase.input.map { "$it" })
        if (out == null) {
            Assert.fail("Program returned with non-zero code")
        }
        testCase.checkOutput(out!!.flatMap { it.split(" ").map { it.toIntOrNull() } })
    }
}
