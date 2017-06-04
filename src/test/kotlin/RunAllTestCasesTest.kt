
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
abstract class RunAllTestCasesTest(val testCaseRunner: TestCaseRunner) {
    @Parameterized.Parameter(value = 0)
    lateinit var testCase: TestCase

    companion object {
        @Parameterized.Parameters(name = "test case: {0}")
        @JvmStatic fun testCases() = allTestCases.toTypedArray().sortedBy { it.name }
    }

    @Test fun runTestCase() {
        testCaseRunner.runTestCase(testCase)
    }
}

class RunAllTestCasesInterpreter : RunAllTestCasesTest(InterpreterRunner())

class RunAllTestCasesStack : RunAllTestCasesTest(StackRunner())

class RunAllTestCasesX86 : RunAllTestCasesTest(X86Runner())