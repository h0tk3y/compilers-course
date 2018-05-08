val simpleTestCases = mutableListOf<TestCase>()
fun TestCase.registerSimple() = also { simpleTestCases.add(it) }
fun Iterable<TestCase>.registerSimple() = apply { forEach { simpleTestCases.add(it) } }

val factorialTestCases = (2..10).map { n ->
    TestCaseCheckOutput("factorial $n", programOf(factorial), listOf(n)) { output -> output.last() == (1..n).reduce(Int::times) }
}.registerSimple()

val fastPowTestCases = (2..3).flatMap { n ->
    (5..6).map { p ->
        TestCaseMatchOutput("fastPow $n^$p", listOf(n, p), listOf(null, null, generateSequence { n }.take(p).reduce(Int::times)),
                            programOf(fastPow))
    }
}.registerSimple()

val funCallTestCases = (4..5).flatMap { x ->
    (9..10).map { y ->
        TestCaseMatchOutput("fun call ($x, $y)", listOf(x, y), listOf(null, null, 3 * x + 5 * y), programOf(addIntsTest, listOf(addInts)))
    }
}.registerSimple()

val simpleParsed = ParsedTestCaseMatchOutput("simpleParsed", """
    x := read();
    y := read();
    z := y*y;
    write (x+z)
    """.trimIndent(), listOf(123, 456), listOf(null, null, 123 + 456 * 456)).registerSimple()

val simpleParsedWithFun = ParsedTestCaseMatchOutput("simpleParsedWithFun", """
    fun multiplyInts(a, b)
    begin
        return a * b
    end

    fun addInts(a, b)
    begin
        return a + b
    end

    x := read();
    y := read();
    z := multiplyInts(y, y);
    write (addInts(x, z))
    """.trimIndent(), listOf(123, 456), listOf(null, null, 123 + 456 * 456)).registerSimple()

val returnInsideFun = ParsedTestCaseMatchOutput("returnInsideFun", """
    fun someFun(a, b, c) begin
        write(a)
        write(b)
        write(c)
        return a;
        return b;
        return c;
    end

    write(someFun(123, 456, 789))
    """.trimIndent(), listOf(), listOf(123, 456, 789, 123)).registerSimple()

val funCallOnLongStack = ParsedTestCaseMatchOutput("funCallOnLongStack", """
    fun someFun(a, b, c) begin
        return a + b * c
    end

    x := 1 + 2 + 3 - 4 + 5 * 6 / 7 % 8 + someFun(1, 2, 3)
    write(x)
    """.trimIndent(), listOf(), listOf(1 + 2 + 3 - 4 + 5 * 6 / 7 % 8 + (1 + 2 * 3))).registerSimple()

val arrsExample = ParsedTestCaseMatchOutput(
    "arrsExample", """
        fun writes(i) begin
            write(i)
            return i
        end

        arr = [writes(101), writes(102), writes(103), writes(104)]
        arr[0] := 1
        arr[1] := 2
        arr[2] := 3
        write(arr[1])
        write(arr[2])
        write(arr[3])
        write(arrlen(arr))
    """.trimIndent(), listOf(), listOf(101, 102, 103, 104, 2, 3, 104, 4)
).registerSimple()

val boxedArrSimple = ParsedTestCaseMatchOutput(
    "boxedArrSimple", """
        A = {[1, 2, 3], [4, 5, 6], [7, 8, 9], 999}
        for i := 0, i < 3, i := i + 1 do
            for j := 0, j < 3, j := j + 1 do
                write(A[i][j])
            od
        od;
        A[0] = [-1, -2, -3]
        A[1][1] = 100
        for i := 0, i < 3, i := i + 1 do
            write(A[0][i])
        od
        write(A[1][1])
        write(A[3])
        write(arrlen(A))
        write(arrlen(A[0]))
    """.trimIndent(), listOf(), listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -2, -3, 100, 999, 4, 3)
).registerSimple()