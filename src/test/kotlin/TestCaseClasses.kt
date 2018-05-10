
import com.github.h0tk3y.compilersCourse.language.Program
import com.github.h0tk3y.compilersCourse.parsing.readProgram
import org.junit.Assert

abstract class TestCase(val name: String, val input: List<Int>, private val programOrNull: Program? = null) {
    abstract fun checkOutput(output: List<Int?>)
    open val program get() = programOrNull!!
    open fun canRunOnRunner(testCaseRunner: TestCaseRunner) = true

    override fun toString(): String = name
}

open class TestCaseCheckOutput(name: String, program: Program, input: List<Int>, val checkOutputAction: (List<Int?>) -> Unit)
    : TestCase(name, input, program) {

    override fun checkOutput(output: List<Int?>) = checkOutputAction(output)
}

open class TestCaseMatchOutput(name: String, input: List<Int>, val exactOutput: List<Int?>, program: Program? = null) : TestCase(name, input, program) {
    override fun checkOutput(output: List<Int?>) {
        Assert.assertEquals(exactOutput, output)
    }
}

open class ParsedTestCaseMatchOutput(name: String, text: String, input: List<Int>, exactOutput: List<Int?>)
    : TestCaseMatchOutput(name, input, exactOutput) {
    override val program: Program by lazy {
        readProgram(text)
    }
}
