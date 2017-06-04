val allTestCases = mutableListOf<TestCase>()
fun Iterable<TestCase>.register() = apply { forEach { allTestCases.add(it) } }

val factorialTestCases = (2..10).map { n ->
    TestCaseCheckOutput("factorial $n", programOf(factorial), listOf(n)) { output -> output.last() == (1..n).reduce(Int::times) }
}.register()

val fastPowTestCases = (2..3).flatMap { n ->
    (5..6).map { p ->
        TestCaseMatchOutput("fastPow $n^$p", programOf(fastPow), listOf(n, p),
                            listOf(generateSequence { n }.take(p).reduce(Int::times)))
    }
}.register()

val funCallTestCases = (4..5).flatMap { x ->
    (9..10).map { y ->
        TestCaseMatchOutput("fun call ($x, $y)", programOf(addIntsTest, listOf(addInts)), listOf(x, y), listOf(3 * x + 5 * y))
    }
}.register()

val simpleParsed = listOf(
        ParsedTestCaseMatchOutput("simple parse", """
        x := read();
        y := read();
        z := y*y;
        write (x+z)
        """.trimIndent(), listOf(123, 456), listOf(123 + 456 * 456))
).register()