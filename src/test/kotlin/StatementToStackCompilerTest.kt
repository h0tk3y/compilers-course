
import com.github.h0tk3y.compilersCourse.run
import com.github.h0tk3y.compilersCourse.stack.NaiveStackInterpreter
import com.github.h0tk3y.compilersCourse.stack.StatementToStackCompiler
import org.junit.Assert
import org.junit.Test

class StatementToStackCompilerTest {
    @Test fun testFactorialCompile() {
        val factorial = programOf(factorial)

        val compiler = StatementToStackCompiler()
        val output = compiler.compile(factorial)
        output.functions.values.single().forEach(::println)
        val interpreter = NaiveStackInterpreter()
        val result = interpreter.run(output, listOf(6))
        Assert.assertEquals(listOf(6, 30, 120, 360, 720), result.output)
        Assert.assertTrue(result.input.isEmpty())
        Assert.assertTrue(result.stack.isEmpty())
        Assert.assertEquals(output.functions.values.single().size, result.instructionPointer)
    }

    @Test fun testFastPowCompile() {
        val fastPow = programOf(fastPow)

        val compiler = StatementToStackCompiler()
        val output = compiler.compile(fastPow)
        output.functions.values.single().forEach(::println)
        val interpreter = NaiveStackInterpreter()
        val result = interpreter.run(output, listOf(2, 11))
        println(result)
    }

    @Test fun testFunctionCall() {
        val program = programOf(addIntsTest, listOf(addInts))

        val compiler = StatementToStackCompiler()
        val code = compiler.compile(program)

        val interpreter = NaiveStackInterpreter()
        val result = interpreter.run(code, listOf())
        Assert.assertEquals(13, result.output.single())
    }
}