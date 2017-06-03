package com.github.h0tk3y.compilersCourse.language

import com.github.h0tk3y.compilersCourse.Interpreter
import com.github.h0tk3y.compilersCourse.run

data class MachineState(val input: List<Int>,
                        val output: List<Int>,
                        val state: (Variable) -> Int,
                        val result: Int = 0)

internal fun ((Variable) -> Int).andMap(mapping: Pair<Variable, Int>): (Variable) -> Int =
        { if (it == mapping.first) mapping.second else this(it) }

class NaiveInterpreter : Interpreter<MachineState, Statement, List<Int>> {
    override fun initialState(input: List<Int>) = MachineState(input, emptyList(), { throw NoSuchElementException() })

    fun Expression.evaluate(machine: MachineState): MachineState = when (this) {
        is Const -> machine.copy(result = value)
        is Variable -> machine.copy(result = machine.state(this))
        is UnaryOperation -> {
            val o = operand.evaluate(machine)
            o.copy(result = kind.semantics(o.result))
        }
        is BinaryOperation -> {
            val l = left.evaluate(machine)
            val r = right.evaluate(l)
            r.copy(result = kind.semantics(l.result, r.result))
        }
        is FunctionCall -> this.functionDeclaration.let { f ->
            var currentMachine = machine
            val innerContext = mutableMapOf<Variable, Int>()
            for ((parameter, expr) in functionDeclaration.parameters.zip(argumentExpressions)) {
                currentMachine = expr.evaluate(currentMachine)
                innerContext[parameter] = currentMachine.result
            }
            val innerMachine = currentMachine.copy(state = { innerContext[it] ?: throw NoSuchElementException() })
            when (f) {
                is Intrinsic -> when (f) {
                    is Intrinsic.READ -> machine.copy(input = machine.input.drop(1), result = machine.input.first())
                    is Intrinsic.WRITE -> machine.copy(output = machine.output + innerContext[Intrinsic.WRITE.parameters.single()]!!)
                }
                else -> join(innerMachine, functionDeclaration.body).copy(state = currentMachine.state)
            }
        }
    }

    override fun join(s: MachineState, p: Statement): MachineState = with(p) {
        when (this) {
            is Skip, is FunctionDeclaration -> s
            is Assign -> expression.evaluate(s).run { copy(state = state.andMap(variable to result)) }
            is If -> condition.evaluate(s).run {
                if (result != 0)
                    join(s, trueBranch) else
                    join(s, falseBranch)
            }
            is While -> {
                val afterCondition = condition.evaluate(s)
                if (afterCondition.result == 0)
                    afterCondition else
                    join(join(afterCondition, body), p)
            }
            is Chain -> join(join(s, leftPart), rightPart)
            is Return -> expression.evaluate(s)
            is FunctionCallStatement -> functionCall.evaluate(s)
        }
    }
}

class NaiveProgramInterpreter : Interpreter<MachineState, Program, List<Int>> {
    val interpreter = NaiveInterpreter()

    override fun initialState(input: List<Int>): MachineState  = interpreter.initialState(input)

    override fun join(s: MachineState, p: Program): MachineState = interpreter.run(p.mainFunction.body, s.input)
}