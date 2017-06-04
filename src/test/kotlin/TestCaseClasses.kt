
import com.github.h0tk3y.compilersCourse.language.Program
import com.github.h0tk3y.compilersCourse.parsing.readProgram
import org.junit.Assert

abstract class TestCase(val name: String, val program: Program, val input: List<Int>) {
    abstract fun checkOutput(output: List<Int>)

    override fun toString(): String = name
}

class TestCaseCheckOutput(name: String, program: Program, input: List<Int>, val checkOutputAction: (List<Int>) -> Unit)
    : TestCase(name, program, input) {

    override fun checkOutput(output: List<Int>) = checkOutputAction(output)
}

open class TestCaseMatchOutput(name: String, program: Program, input: List<Int>, val exactOutput: List<Int>) : TestCase(name, program, input) {
    override fun checkOutput(output: List<Int>) {
        Assert.assertEquals(exactOutput, output)
    }
}

class ParsedTestCaseMatchOutput(name: String, text: String, input: List<Int>, exactOutput: List<Int>)
    : TestCaseMatchOutput(name, readProgram(text), input, exactOutput)
