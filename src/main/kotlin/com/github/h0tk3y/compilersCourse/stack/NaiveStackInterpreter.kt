package com.github.h0tk3y.compilersCourse.stack

import com.github.h0tk3y.compilersCourse.Interpreter
import com.github.h0tk3y.compilersCourse.exhaustive
import com.github.h0tk3y.compilersCourse.language.Intrinsic
import com.github.h0tk3y.compilersCourse.language.Variable
import com.github.h0tk3y.compilersCourse.language.andMap
import com.github.h0tk3y.compilersCourse.language.semantics

data class StackMachineState(val input: List<Int>,
                             val output: List<Int?>,
                             val state: (Variable) -> Int,
                             val stack: List<Int>,
                             val instructionPointer: Int,
                             val stringPool: List<CharArray>)

class NaiveStackInterpreter() : Interpreter<StackMachineState, StackProgram, List<Int>> {
    override fun initialState(input: List<Int>) =
            StackMachineState(input, emptyList(), { throw NoSuchElementException() }, emptyList(), 0, emptyList())

    private fun StackMachineState.pop(n: Int) = copy(stack = stack.dropLast(n))
    private fun StackMachineState.push(c: Int) = copy(stack = stack + c)
    private fun StackMachineState.addNewStr(str: CharArray) = copy(stack = stack + stringPool.size, stringPool = stringPool + str)

    fun step(s: StackMachineState, p: StackProgram) = with(s) {
        val code = p.functions[p.entryPoint]!!
        val t = code[s.instructionPointer]
        val step = when (t) {
            Nop -> this
            is Push -> copy(stack = stack + t.constant.value)
            is PushPooled -> copy(stack = stack + t.id)
            is Ld -> copy(stack = stack + state(t.v))
            is St -> copy(stack = stack.dropLast(1), state = state.andMap(t.v to stack.last()))
            is Unop ->
                copy(stack = stack.dropLast(1) + t.kind.semantics(stack.last()))
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
                        Intrinsic.READ -> copy(input = input.drop(1), stack = stack + input.first(), output = output + null as Int?)
                        Intrinsic.WRITE -> copy(output = output + stack.last(), stack = stack.dropLast(1))
                        Intrinsic.STRMAKE -> {
                            val (c, n) = stack.reversed()
                            pop(2).addNewStr(CharArray(n) { c.toChar() })
                        }
                        Intrinsic.STRCMP -> {
                            val (s2, s1) = stack.reversed()
                            val cmpResult = String(stringPool[s1]).compareTo(String(stringPool[s2]))
                            pop(2).push(cmpResult)
                        }
                        Intrinsic.STRGET -> {
                            val (i, str) = stack.reversed()
                            val result = stringPool[str][i].toInt()
                            pop(2).push(result)
                        }
                        Intrinsic.STRDUP -> {
                            val str = stack.last()
                            pop(1).addNewStr(stringPool[str].copyOf())
                        }
                        Intrinsic.STRSET -> {
                            val (c, i, str) = stack.reversed()
                            stringPool[str][i] = c.toChar()
                            pop(3).push(0)
                        }
                        Intrinsic.STRCAT -> {
                            val (s2, s1) = stack.reversed()
                            pop(2).addNewStr(stringPool[s1] + stringPool[s2])
                        }
                        Intrinsic.STRSUB -> {
                            val (n, from, str) = stack.reversed()
                            pop(3).addNewStr(stringPool[str].copyOfRange(from, from + n))
                        }
                        Intrinsic.STRLEN -> {
                            val str = stack.last()
                            pop(1).push(stringPool[str].size)
                        }
                    }.exhaustive
                    else -> {
                        val internalMachine = StackMachineState(
                                input, output,
                                f.parameters.zip(stack.takeLast(f.parameters.size)).toMap()::getValue,
                                emptyList(), 0, stringPool)
                        val result = join(internalMachine, StackProgram(p.functions, f, p.literalPool))
                        val returnValue = result.stack.last()
                        copy(result.input, result.output,
                             stack = stack.dropLast(f.parameters.size) + returnValue,
                             stringPool = result.stringPool)
                    }
                }.exhaustive
            }
            Ret1 -> copy(instructionPointer = code.lastIndex)
            Ret0 -> copy(instructionPointer = code.lastIndex, stack = stack + 0)
            Pop -> copy(stack = stack.dropLast(1))
        }.exhaustive
        step.copy(instructionPointer = step.instructionPointer + 1)
    }

    override fun join(s: StackMachineState, p: StackProgram): StackMachineState =
            generateSequence(if (s.stringPool.isEmpty()) s.copy(stringPool = p.literalPool) else s) {
                if (it.instructionPointer !in p.code.indices) null else step(it, p)
            }.last()

}