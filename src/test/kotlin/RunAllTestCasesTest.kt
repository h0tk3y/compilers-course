
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
abstract class RunAllTestCases {
    @Parameterized.Parameter(value = 0)
    lateinit var testCase: TestCase

    val interpreterRunner = InterpreterRunner()
    val stackRunner = StackRunner()
    val x86Runner = X86Runner()

    @Test fun runWithInterpreter() {
        interpreterRunner.runTestCase(testCase)
    }

    @Test fun runWithStack() {
        stackRunner.runTestCase(testCase)
    }

    @Ignore @Test fun runWithX86() {
        x86Runner.runTestCase(testCase)
    }
}

@RunWith(Parameterized::class)
class RunCoreTestCases : RunAllTestCases() {
    companion object {
        @Parameterized.Parameters(name = "test case: {0}")
        @JvmStatic fun testCases() = coreTestsNoArrays
    }
}

@RunWith(Parameterized::class)
class RunExpressionTestCases : RunAllTestCases() {
    companion object {
        @Parameterized.Parameters(name = "test case: {0}")
        @JvmStatic fun testCases() = expressionTests
    }
}

@RunWith(Parameterized::class)
class RunDeepExpressionTestCases : RunAllTestCases() {
    companion object {
        @Parameterized.Parameters(name = "test case: {0}")
        @JvmStatic fun testCases() = deepExpressionTests
    }
}