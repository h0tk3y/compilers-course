package com.github.h0tk3y.compilersCourse

import com.github.h0tk3y.compilersCourse.language.*

data class MachineState(val input: List<Int>,
                        val output: List<Int>,
                        val state: (Variable) -> Int)

internal fun ((Variable) -> Int).andMap(mapping: Pair<Variable, Int>): (Variable) -> Int =
        { if (it == mapping.first) mapping.second else this(it) }

class NaiveInterpreter : Interpreter<MachineState, Statement, List<Int>> {
    override fun initialState(input: List<Int>) = MachineState(input, emptyList(), { throw NoSuchElementException() })

    fun Expression.evaluate(state: (Variable) -> Int): Int = when (this) {
        is Const -> value
        is Variable -> state(this)
        is UnaryOperation -> {
            val o = operand.evaluate(state)
            kind.semantics(o)
        }
        is BinaryOperation -> {
            val l = left.evaluate(state)
            val r = right.evaluate(state)
            kind.semantics(l, r)
        }
    }

    override fun join(s: MachineState, p: Statement): MachineState = with(p) {
        when (this) {
            is Skip -> s
            is Assign -> s.copy(state = s.state.andMap(variable to expression.evaluate(s.state)))
            is Read -> s.copy(state = s.state.andMap(variable to s.input.first()), input = s.input.drop(1))
            is Write -> s.copy(output = s.output + expression.evaluate(s.state))
            is If -> if (condition.evaluate(s.state) != 0)
                join(s, trueBranch) else
                join(s, falseBranch)
            is While -> if (condition.evaluate(s.state) == 0)
                s else
                join(join(s, body), p)
            is Chain -> join(join(s, leftPart), rightPart)
        }
    }
}