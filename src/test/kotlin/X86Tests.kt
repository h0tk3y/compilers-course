
import com.github.h0tk3y.compilersCourse.x86.StatementToX86Compiler
import com.github.h0tk3y.compilersCourse.x86.TargetPlatform
import org.junit.Test

class X86Tests {
    val compiler = StatementToX86Compiler(TargetPlatform.WIN)

    @Test fun testFactorial() {
        val output = compiler.compile(programOf(factorial))
        println(output)
    }

    @Test fun testFastPow() {
        val output = compiler.compile(programOf(fastPow))
        println(output)
    }

    @Test fun funCall() {
        val output = compiler.compile(programOf(addIntsTest, listOf(addInts)))
        println(output)
    }
}