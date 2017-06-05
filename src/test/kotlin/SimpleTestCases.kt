val allTestCases = mutableListOf<TestCase>()
fun TestCase.register() = also { allTestCases.add(it) }
fun Iterable<TestCase>.register() = apply { forEach { allTestCases.add(it) } }

val triggerInit = run { simpleParsed; simpleParsedWithFun; }

val factorialTestCases = (2..10).map { n ->
    TestCaseCheckOutput("factorial $n", programOf(factorial), listOf(n)) { output -> output.last() == (1..n).reduce(Int::times) }
}.register()

val fastPowTestCases = (2..3).flatMap { n ->
    (5..6).map { p ->
        TestCaseMatchOutput("fastPow $n^$p", listOf(n, p), listOf(null, null, generateSequence { n }.take(p).reduce(Int::times)),
                            programOf(fastPow))
    }
}.register()

val funCallTestCases = (4..5).flatMap { x ->
    (9..10).map { y ->
        TestCaseMatchOutput("fun call ($x, $y)", listOf(x, y), listOf(null, null, 3 * x + 5 * y), programOf(addIntsTest, listOf(addInts)))
    }
}.register()