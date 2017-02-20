import java.util.*

data class MachineState(val input: List<Int>,
                        val output: List<Int>,
                        val state: (Variable) -> Int,
                        val s: Statement)

private fun ((Variable) -> Int).andMap(v: Variable, value: Int): (Variable) -> Int = { if (it == v) value else this(it) }

fun interpret(machineState: MachineState): MachineState = with(machineState) {
    when (s) {
        is Skip -> this
        is Assign -> copy(
                state = state.andMap(s.variable, s.expression.evaluate(state)),
                s = Skip())
        is Read -> copy(
                state = state.andMap(s.variable, input.first()),
                input = input.drop(1),
                s = Skip())
        is Write -> copy(
                output = output + s.expression.evaluate(state),
                s = Skip())
        is If -> if (s.condition.evaluate(state) != 0)
            interpret(copy(s = s.trueBranch)) else
            interpret(copy(s = s.falseBranch))
        is While -> if (s.condition.evaluate(state) == 0)
            copy(s = Skip()) else
            interpret(interpret(copy(s = s.body)).copy(s = s))
        is Chain -> interpret(interpret(copy(s = s.leftPart)).copy(s = s.rightPart))
    }
}

fun interpret(statement: Statement, input: List<Int>) =
        interpret(MachineState(input, emptyList(), { throw NoSuchElementException() }, statement))

fun main(args: Array<String>) {
    val v1 = Variable("v1")
    val v2 = Variable("v2")
    val v3 = Variable("result")
    val s = chainOf(
            Read(v1),
            Assign(v2, Plus(v1, v1)),
            Assign(v1, Times(v2, v2)),
            If(Eq(v1, Const(16)), Write(Const(1)), Write(Const(0))))

    val factorial = chainOf(
            Read(v1),
            Assign(v2, Const(1)),
            While(Gt(v1, Const(1)), chainOf(
                    Assign(v2, Times(v2, v1)),
                    Assign(v1, Minus(v1, Const(1))),
                    Write(v2)
            )))

    val fastPow = chainOf(
            Read(v1),
            Assign(v3, Const(1)),
            Read(v2),
            While(Gt(v2, Const(0)), chainOf(
                    If(Eq(Rem(v2, Const(2)), Const(1)), chainOf(
                            Assign(v3, Times(v3, v1)),
                            Assign(v2, Minus(v2, Const(1)))
                    ), Skip()),
                    Assign(v1, Times(v1, v1)),
                    Assign(v2, Div(v2, Const(2)))
            )),
            Write(v3))

    println(interpret(factorial, listOf(10)).output)
    println(interpret(fastPow, listOf(2, 16)).output)
}