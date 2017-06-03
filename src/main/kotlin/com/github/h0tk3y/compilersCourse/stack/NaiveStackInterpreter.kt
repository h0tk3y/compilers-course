package com.github.h0tk3y.compilersCourse.stack

import com.github.h0tk3y.compilersCourse.Interpreter
import com.github.h0tk3y.compilersCourse.exhaustive
import com.github.h0tk3y.compilersCourse.language.Intrinsic
import com.github.h0tk3y.compilersCourse.language.Variable
import com.github.h0tk3y.compilersCourse.language.andMap
import com.github.h0tk3y.compilersCourse.language.semantics

data class StackMachineState(val input: List<Int>,
                             val output: List<Int>,
                             val state: (Variable) -> Int,
                             val stack: List<Int>,
                             val instructionPointer: Int)

class NaiveStackInterpreter() : Interpreter<StackMachineState, StackProgram, List<Int>> {
    override fun initialState(input: List<Int>) =
            StackMachineState(input, emptyList(), { throw NoSuchElementException() }, emptyList(), 0)

    fun step(s: StackMachineState, p: StackProgram) = with(s) {
        val code = p.functions[p.entryPoint]!!
        val t = code[s.instructionPointer]
        val step = when (t) {
            Nop -> this
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
            is Call -> t.function.let { f ->
                when (f) {
                    is Intrinsic -> when (f) {
                        Intrinsic.READ -> copy(input = input.drop(1), stack = stack + input.first())
                        Intrinsic.WRITE -> copy(output = output + stack.last(), stack = stack.dropLast(1))
                    }.exhaustive
                    else -> {
                        val internalMachine = StackMachineState(
                                input, output,
                                f.parameters.zip(stack.takeLast(f.parameters.size)).toMap()::getValue,
                                emptyList(), 0)
                        val result = join(internalMachine, StackProgram(p.functions, f))
                        val returnValue = result.stack.last()
                        copy(result.input, result.output, stack = stack + returnValue)
                    }
                }.exhaustive
            }
            PreArgs -> this
            Ret1 -> copy(instructionPointer = code.lastIndex)
            Ret0 -> copy(instructionPointer = code.lastIndex, stack = stack + 0)
            Pop -> copy(stack = stack.dropLast(1))
        }.exhaustive
        step.copy(instructionPointer = step.instructionPointer + 1)
    }

    override fun join(s: StackMachineState, p: StackProgram): StackMachineState =
            generateSequence(s) { if (it.instructionPointer !in p.code.indices) null else step(it, p) }.last()

}