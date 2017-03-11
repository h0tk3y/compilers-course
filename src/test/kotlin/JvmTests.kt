
import com.github.h0tk3y.compilersCourse.jvm.StatementToJvmCompiler
import com.github.h0tk3y.compilersCourse.language.*
import org.junit.Test
import java.io.File

class JvmTests {
    @Test fun testFactorial() {
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

        val compiler = StatementToJvmCompiler()
        val r = compiler.compile(factorial)
        File("Program.class").writeBytes(r)
    }

    @Test fun testFastPow() {
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

        val compiler = StatementToJvmCompiler()
        val r = compiler.compile(fastPow)
        File("Program.class").writeBytes(r)
    }
}