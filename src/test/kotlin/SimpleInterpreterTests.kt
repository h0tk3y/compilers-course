
import com.github.h0tk3y.compilersCourse.language.NaiveProgramInterpreter
import com.github.h0tk3y.compilersCourse.run
import org.junit.Assert
import org.junit.Test

class SimpleInterpreterTests {
    val interpreter = NaiveProgramInterpreter()

    @Test fun testFactorial() {
        fun check(n: Int) = Assert.assertEquals(
                (1..n).reduce(Int::times),
                interpreter.run(programOf(factorial), listOf(n)).output.last())

        (2..10).forEach(::check)
    }

    @Test fun testFastPow() {
        fun check(n: Int, p: Int) = Assert.assertEquals(
                generateSequence { n }.take(p).reduce(Int::times),
                interpreter.run(programOf(fastPow), listOf(n, p)).output.last())

        (1..10).forEach { n -> (1..9).forEach { p -> check(n, p) } }
    }

    @Test fun funCall() {
        val program = programOf(addIntsTest, listOf(addInts))
        val output = interpreter.run(program, listOf()).output.single()
        Assert.assertEquals(13, output)
    }
}