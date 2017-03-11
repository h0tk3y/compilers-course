import com.github.h0tk3y.compilersCourse.language.*
import com.github.h0tk3y.compilersCourse.run
import com.github.h0tk3y.compilersCourse.stack.NaiveStackInterpreter
import com.github.h0tk3y.compilersCourse.stack.StatementToStackCompiler
import org.junit.Assert
import org.junit.Test

class StatementToStackCompilerTest {
    @Test fun testFactorialCompile() {
        val v1 = Variable("v1")
        val v2 = Variable("v2")

        val factorial = chainOf(
                Read(v1),
                Assign(v2, Const(1)),
                While(v1 greaterThan Const(1), chainOf(
                        Assign(v2, v2 * v1),
                        Assign(v1, v1 - Const(1)),
                        Write(v2)
                )))

        val compiler = StatementToStackCompiler()
        val output = compiler.compile(factorial)
        output.forEach(::println)
        val interpreter = NaiveStackInterpreter()
        val result = interpreter.run(output, listOf(6))
        Assert.assertEquals(listOf(6, 30, 120, 360, 720), result.output)
        Assert.assertTrue(result.input.isEmpty())
        Assert.assertTrue(result.stack.isEmpty())
        Assert.assertEquals(output.size, result.instructionPointer)
    }

    @Test fun testFastPowCompile() {
        val v1 = Variable("v1")
        val v2 = Variable("v2")
        val v3 = Variable("v3")

        val fastPow = chainOf(
                Assign(v3, Const(1)),
                Read(v1),
                Read(v2),
                While(v2 greaterThan Const(0), chainOf(
                        If(v2 % Const(2) eq Const(1), chainOf(
                                Assign(v3, v3 * v1),
                                Assign(v2, v2 - Const(1))
                        ), Skip),
                        Assign(v1, v1 * v1),
                        Assign(v2, v2 / Const(2))
                )),
                Write(v3))

        val compiler = StatementToStackCompiler()
        val output = compiler.compile(fastPow)
        output.forEach(::println)
        val interpreter = NaiveStackInterpreter()
        val result = interpreter.run(output, listOf(2, 11))
        println(result)
    }
}