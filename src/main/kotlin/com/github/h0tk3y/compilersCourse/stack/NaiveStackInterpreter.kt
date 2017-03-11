package com.github.h0tk3y.compilersCourse.stack

import com.github.h0tk3y.compilersCourse.Interpreter
import com.github.h0tk3y.compilersCourse.andMap
import com.github.h0tk3y.compilersCourse.language.Variable
import com.github.h0tk3y.compilersCourse.language.semantics

data class StackMachineState(val input: List<Int>,
                             val output: List<Int>,
                             val state: (Variable) -> Int,
                             val stack: List<Int>,
                             val instructionPointer: Int)

class NaiveStackInterpreter() : Interpreter<StackMachineState, List<StackStatement>, List<Int>> {
    override fun initialState(input: List<Int>) =
            StackMachineState(input, emptyList(), { throw NoSuchElementException() }, emptyList(), 0)

    fun step(s: StackMachineState, p: List<StackStatement>) = with(s) {
        val t = p[s.instructionPointer]
        val step = when (t) {
            Nop -> this
            Rd -> copy(input = input.drop(1), stack = stack + input.first())
            Wr -> copy(output = output + stack.last(), stack = stack.dropLast(1))
            is Push -> copy(stack = stack + t.constant.value)
            is Ld -> copy(stack = stack + state(t.v))
            is St -> copy(stack = stack.dropLast(1), state = state.andMap(t.v to stack.last()))
            is Unop -> copy(stack = stack.dropLast(1) + t.kind.semantics(stack.last()))
            is Binop -> {
                val l = stack[stack.lastIndex - 1]
                val r = stack.last()
                copy(stack = stack.dropLast(2) + t.kind.semantics(l, r))
            }
            is Jmp -> copy(instructionPointer = t.nextInstruction - 1)
            is Jz -> copy(stack = stack.dropLast(1),
                          instructionPointer = if (stack.last() != 0)
                                    instructionPointer else
                                    t.nextInstruction - 1)
        }
        step.copy(instructionPointer = step.instructionPointer + 1)
    }

    override fun join(s: StackMachineState, p: List<StackStatement>): StackMachineState =
            generateSequence(s) { if (it.instructionPointer !in p.indices) null else step(it, p) }.last()

}