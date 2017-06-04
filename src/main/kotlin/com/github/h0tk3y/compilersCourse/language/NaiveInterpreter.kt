package com.github.h0tk3y.compilersCourse.language

import com.github.h0tk3y.compilersCourse.Interpreter

data class MachineState(val input: List<Int>,
                        val output: List<Int?>,
                        val state: Map<Variable, Int>,
                        val result: Int = 0,
                        val functionReferences: Map<FunctionDeclaration, Statement>)

internal fun ((Variable) -> Int).andMap(mapping: Pair<Variable, Int>): (Variable) -> Int =
        { if (it == mapping.first) mapping.second else this(it) }

class NaiveInterpreter : Interpreter<MachineState, Statement, List<Int>> {
    override fun initialState(input: List<Int>) =
            MachineState(input, emptyList(), emptyMap(), 0, emptyMap())

    fun Expression.evaluate(machine: MachineState): MachineState = when (this) {
        is Const -> machine.copy(result = value)
        is Variable -> machine.copy(result = machine.state.getValue(this))
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
            val innerMachine = currentMachine.copy(state = innerContext)
            when (f) {
                is Intrinsic -> when (f) {
                    is Intrinsic.READ -> currentMachine.copy(input = currentMachine.input.drop(1), output = currentMachine.output.plus(null as Int?), result = currentMachine.input.first())
                    is Intrinsic.WRITE -> currentMachine.copy(output = currentMachine.output + innerContext[Intrinsic.WRITE.parameters.single()]!!)
                }
                else -> {
                    val functionBody = currentMachine.functionReferences[f] ?: throw IllegalStateException("Call to an unresolved function $f")
                    join(innerMachine, functionBody).copy(state = currentMachine.state)
                }
            }
        }
    }

    override fun join(s: MachineState, p: Statement): MachineState = with(p) {
        when (this) {
            is Skip, is FunctionDeclaration -> s
            is Assign -> expression.evaluate(s).run { copy(state = state.plus(variable to result)) }
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

    override fun initialState(input: List<Int>): MachineState = interpreter.initialState(input)

    override fun join(s: MachineState, p: Program): MachineState =
            interpreter.join(s.copy(functionReferences = p.functionDeclarations.associate { it to it.body }),
                             p.mainFunction.body)
}