package com.github.h0tk3y.compilersCourse.language

import com.github.h0tk3y.compilersCourse.Interpreter
import com.github.h0tk3y.compilersCourse.exhaustive

data class MachineState(val input: List<Int>,
                        val output: List<Int?>,
                        val state: Map<Variable, Any>,
                        val result: Any,
                        val shouldReturn: Boolean,
                        val exceptionType: ExceptionType? = null,
                        val functionReferences: Map<FunctionDeclaration, Statement>)

internal fun ((Variable) -> Int).andMap(mapping: Pair<Variable, Int>): (Variable) -> Int =
        { if (it == mapping.first) mapping.second else this(it) }

internal fun Map<Variable, Int>.andMap(mapping: Pair<Variable, Int>): Map<Variable, Int> =
    this + mapping

fun Intrinsic.runOn(argMap: Map<Variable, Any>, currentMachine: MachineState): MachineState {
    val ps = this.parameters
    return when (this) {
        Intrinsic.READ -> currentMachine.copy(input = currentMachine.input.drop(1),
                                              output = currentMachine.output.plus(null as Int?),
                                              result = currentMachine.input.first())
        Intrinsic.WRITE -> currentMachine.copy(output = currentMachine.output + argMap[ps[0]]!! as Int)
        Intrinsic.STRMAKE -> currentMachine.copy(result = CharArray(argMap[ps[0]] as Int) { (argMap[ps[1]] as Int).toChar() })
        Intrinsic.STRCMP -> currentMachine.copy(result = String(argMap[ps[0]] as CharArray).compareTo(String(argMap[ps[1]] as CharArray)))
        Intrinsic.STRGET -> currentMachine.copy(result = (argMap[ps[0]] as CharArray)[argMap[ps[1]] as Int].toInt())
        Intrinsic.STRDUP -> currentMachine.copy(result = (argMap[ps[0]] as CharArray).copyOf())
        Intrinsic.STRSET -> {
            val s = argMap[ps[0]] as CharArray
            val i = argMap[ps[1]] as Int
            val c = argMap[ps[2]] as Int
            currentMachine.copy(result = 0.apply { s[i] = c.toChar() })
        }
        Intrinsic.STRCAT -> {
            val s1 = argMap[ps[0]] as CharArray
            val s2 = argMap[ps[1]] as CharArray
            currentMachine.copy(result = s1 + s2)
        }
        Intrinsic.STRSUB -> {
            val s = argMap[ps[0]] as CharArray
            val from = argMap[ps[1]] as Int
            val n = argMap[ps[2]] as Int
            currentMachine.copy(result = s.copyOfRange(from, from + n))
        }
        Intrinsic.STRLEN -> currentMachine.copy(result = (argMap[ps[0]] as CharArray).size)
        Intrinsic.ARRMAKE -> TODO()
        Intrinsic.ARRMAKEBOX -> TODO()
        Intrinsic.ARRGET -> TODO()
        Intrinsic.ARRSET -> TODO()
    }
}

class NaiveInterpreter : Interpreter<MachineState, Statement, List<Int>> {
    override fun initialState(input: List<Int>) =
            MachineState(input, emptyList(), emptyMap(), 0, false, null, emptyMap())

    fun Expression.evaluate(machine: MachineState): MachineState = when (this) {
        is Const -> machine.copy(result = value)
        is Variable ->
            machine.copy(result = machine.state.getValue(this))
        is UnaryOperation -> {
            val o = operand.evaluate(machine)
            if (o.shouldReturn)
                o else
                o.copy(result = kind.semantics(o.result as Int))
        }
        is BinaryOperation -> {
            val l = left.evaluate(machine)
            if (l.shouldReturn)
                l else {
                val r = right.evaluate(l)
                if (r.shouldReturn)
                    r else
                    r.copy(result = kind.semantics(l.result as Int, r.result as Int))
            }
        }
        is FunctionCall -> this.functionDeclaration.let { f ->
            var currentMachine = machine
            val innerContext = mutableMapOf<Variable, Any>()
            for ((parameter, expr) in functionDeclaration.parameters.zip(argumentExpressions)) {
                currentMachine = expr.evaluate(currentMachine)
                if (currentMachine.shouldReturn)
                    break
                innerContext[parameter] = currentMachine.result
            }
            if (currentMachine.shouldReturn)
                currentMachine else
                when (f) {
                    is Intrinsic -> f.runOn(innerContext, currentMachine)
                    else -> {
                        val innerMachine = currentMachine.copy(state = innerContext)
                        val functionBody = currentMachine.functionReferences[f] ?: throw IllegalStateException("Call to an unresolved function $f")
                        join(innerMachine, functionBody).run {
                            copy(state = currentMachine.state, shouldReturn = if (exceptionType == null) false else shouldReturn)
                        }
                    }
                }.exhaustive
        }
        is StringLiteral -> {
            machine.copy(result = value.toCharArray())
        }
    }

    override fun join(s: MachineState, p: Statement): MachineState = with(p) {
        when (this) {
            is Skip, is FunctionDeclaration -> s
            is Assign -> expression.evaluate(s).run {
                if (shouldReturn)
                    this else
                    copy(state = state.plus(variable to result))
            }
            is If -> condition.evaluate(s).run {
                when {
                    shouldReturn -> this
                    result != 0 -> join(this, trueBranch)
                    else -> join(this, falseBranch)
                }
            }
            is While -> {
                val afterCondition = condition.evaluate(s)
                if (afterCondition.shouldReturn || afterCondition.result == 0)
                    afterCondition else
                    join(join(afterCondition, body), p)
            }
            is Chain -> {
                join(s, leftPart).run {
                    if (shouldReturn)
                        this else
                        join(this, rightPart)
                }
            }
            is Return -> expression.evaluate(s).copy(shouldReturn = true)
            is FunctionCallStatement -> functionCall.evaluate(s)
            is Throw -> {
                val beforeThrow = dataExpression.evaluate(s)
                if (beforeThrow.exceptionType != null)
                    beforeThrow else
                    beforeThrow.copy(exceptionType = exceptionType, shouldReturn = true)
            }
            is Try -> {
                val afterBody = join(s, body)
                val beforeFinally =
                        if (afterBody.exceptionType != null) {
                            val catchBlock = catchBranches.find { it.exceptionType == afterBody.exceptionType }
                            if (catchBlock == null) {
                                afterBody
                            } else {
                                val afterCatchBody =
                                    join(afterBody.copy(state = afterBody.state + (catchBlock.dataVariable to afterBody.result),
                                                    exceptionType = null, shouldReturn = false),
                                     catchBlock.body)
                                afterCatchBody.copy(state = afterCatchBody.state - catchBlock.dataVariable)
                            }
                        } else afterBody
                join(beforeFinally.copy(exceptionType = null, shouldReturn = false), finallyStatement).run {
                    if (exceptionType != null)
                        this else
                        copy(shouldReturn = shouldReturn || beforeFinally.shouldReturn,
                             result = if (shouldReturn) result else beforeFinally.result,
                             exceptionType = if (shouldReturn) exceptionType else beforeFinally.exceptionType)
                }
            }
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