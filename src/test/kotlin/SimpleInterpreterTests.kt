import com.github.h0tk3y.compilersCourse.NaiveInterpreter
import com.github.h0tk3y.compilersCourse.language.*
import com.github.h0tk3y.compilersCourse.run
import org.junit.Assert
import org.junit.Test

class SimpleInterpreterTests {
    val v1 = Variable("v1")
    val v2 = Variable("v2")
    val v3 = Variable("result")

    val interpreter = NaiveInterpreter()

    @Test fun testFactorial() {
        val factorial = chainOf(
                Read(v1),
                Assign(v2, Const(1)),
                While(v1 greaterThan Const(1), chainOf(
                        Assign(v2, v2 * v1),
                        Assign(v1, v1 - Const(1)),
                        Write(v2)
                )))

        fun check(n: Int) = Assert.assertEquals(
                (1..n).reduce(Int::times),
                interpreter.run(factorial, listOf(n)).output.last())

        (2..10).forEach(::check)
    }

    @Test fun testFastPow() {
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

        fun check(n: Int, p: Int) = Assert.assertEquals(
                generateSequence { n }.take(p).reduce(Int::times),
                interpreter.run(fastPow, listOf(n, p)).output.last())

        (1..10).forEach { n -> (1..9).forEach { p -> check(n, p) } }
    }
}