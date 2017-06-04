val simpleParsed = ParsedTestCaseMatchOutput("simpleParsed", """
    x := read();
    y := read();
    z := y*y;
    write (x+z)
    """.trimIndent(), listOf(123, 456), listOf(null, null, 123 + 456 * 456)).register()

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
    """.trimIndent(), listOf(123, 456), listOf(null, null, 123 + 456 * 456)).register()