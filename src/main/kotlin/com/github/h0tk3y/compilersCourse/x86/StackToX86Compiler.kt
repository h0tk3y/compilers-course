package com.github.h0tk3y.compilersCourse.x86

import com.github.h0tk3y.compilersCourse.Compiler
import com.github.h0tk3y.compilersCourse.language.*
import com.github.h0tk3y.compilersCourse.stack.*
import com.github.h0tk3y.compilersCourse.x86.StackToX86Compiler.SymbolicStackLocation.programStack
import java.util.*
import kotlin.math.absoluteValue

enum class TargetPlatform { UNIX, WIN }

class StackToX86Compiler(val targetPlatform: TargetPlatform) : Compiler<StackProgram, String> {

    fun formatFunctionName(name: String) = when (targetPlatform) {
        TargetPlatform.UNIX -> name
        TargetPlatform.WIN -> "_$name"
    }

    fun formatPooledStringId(id: Int) = "_pooled_string_$id"

    class CompilationEnvironment(val program: StackProgram) {
        val result = mutableListOf<String>()
        fun emit(s: String) = result.add(s).run { }

        fun emitPush(src: String): String {
            val alreadyInUnallocatedRegister =
                SymbolicStackLocation.values().find { it.isRegister && it.name == src.removePrefix("%") }
            if (alreadyInUnallocatedRegister != null) {
                symbolicStack.push(alreadyInUnallocatedRegister)
                emit("# pseudo push: ${prettyPrintStack(1)}")
                return "%" + alreadyInUnallocatedRegister
            } else {
                val location = allocOnSymStack()
                if (location.isRegister) {
                    val regName = "%" + location.name
                    if (regName != src) {
                        emit("movl $src, $regName # ${prettyPrintStack(1)}")
                    } else {
                        emit("# virtual push ${prettyPrintStack(1)}")
                    }
                    return regName
                } else {
                    emit("pushl $src # ${prettyPrintStack(1)}")
                    return "\$esp"
                }
            }
        }

        fun emitPop(dst: String) {
            val location = symbolicStack.pop()
            when (location) {
                programStack -> emit("popl $dst # ${prettyPrintStack(-1)}")
                else -> {
                    val regName = "%" + location.name
                    emit("movl $regName, $dst # ${prettyPrintStack(-1)}")
                }
            }
        }

        fun popToReg1(toRegWhenNotSuitable: String = "%eax", requires8BitOps: Boolean = false): String {
            val currentLocation = symbolicStack.peek()
            return if (currentLocation == programStack || requires8BitOps && !currentLocation.supports8Bits) {
                emitPop(toRegWhenNotSuitable)
                toRegWhenNotSuitable
            } else {
                emit("# virtual pop: ${prettyPrintStack(-1)}")
                "%${symbolicStack.pop().name}"
            }
        }

        fun popTo2Regs(require8BitOps: Boolean) = popToReg1("%ebx", require8BitOps) to popToReg1("%eax", require8BitOps)

        private fun allocOnSymStack(): SymbolicStackLocation {
            val nextItem = SymbolicStackLocation.values().firstOrNull { it !in symbolicStack } ?: programStack
            symbolicStack.push(nextItem)
            return nextItem
        }

        val symbolicStackAtInsn = sortedMapOf<Int, Stack<SymbolicStackLocation>>()

        var symbolicStack = Stack<SymbolicStackLocation>()

        private fun prettyPrintStack(lastSizeChange: Int) = when {
            lastSizeChange == 0 -> symbolicStack.joinToString(" ")
            lastSizeChange > 0 -> symbolicStack.dropLast(1).joinToString(" ") + " -> " + symbolicStack.lastOrNull()?.let { "$it" }.orEmpty()
            else -> symbolicStack.dropLast(1).joinToString(" ") + " <- " + symbolicStack.lastOrNull()?.let { "$it" }.orEmpty()
        }
    }

    enum class SymbolicStackLocation(val isRegister: Boolean = true, val supports8Bits: Boolean = true) {
        ecx, edx, esi(supports8Bits = false), edi(supports8Bits = false), programStack(isRegister = false, supports8Bits = false)
    }

    private fun lower8Bits(percentRegisterName: String) = when (percentRegisterName) {
        "%eax" -> "%al"
        "%ebx" -> "%bl"
        "%ecx" -> "%cl"
        "%edx" -> "%dl"
        "%esi" -> "%si"
        "%edi" -> "%di"
        else -> error("Unknown general purpose register")
    }

    private fun CompilationEnvironment.compileFunction(functionDeclaration: FunctionDeclaration, source: List<StackStatement>) {
        functionDeclaration.name.let { fName ->
            emit(".globl ${formatFunctionName(fName)}")
            emit("${formatFunctionName(fName)}:")
        }

        symbolicStackAtInsn.clear()

        emit("pushl %ebp")
        emit("movl %esp, %ebp")

        val functionScope = (collectVariables(source)).distinct()
        val locals = functionScope - functionDeclaration.parameters
        val localOffsets = locals.asSequence().zip(generateSequence(-4) { it - 4 }).toMap()
        val paramsBoundary = 2 * 4 // 2 words for the previous frame's EBP and the return address
        val paramOffsets = functionDeclaration.parameters.asSequence().zip(generateSequence(paramsBoundary) { it + 4 }).toMap()
        locals.forEach { emit("pushl $0") }

        val varOffsets = localOffsets + paramOffsets

        var insnCanContinue: Boolean

        fun compileInstruction(i: Int, s: StackStatement) {
            insnCanContinue = true

            if (i in symbolicStackAtInsn) {
                symbolicStack = symbolicStackAtInsn[i]!!
            }

            when (s) {
                Nop -> Unit
                is Push -> emitPush("\$${s.constant.value}")
                is PushPooled -> emitPush("\$" + formatPooledStringId(s.id))
                is Ld -> emitPush("${varOffsets[s.v]}(%ebp)")
                is St -> emitPop("${varOffsets[s.v]}(%ebp)")
                is Unop -> when (s.kind) {
                    Not -> {
                        val reg = popToReg1()
                        emit("cmp $0, $reg")
                        emit("jnz ${functionDeclaration.name}_l${i}_nz")
                        val targetLoc = emitPush("$1")
                        emit("jmp ${functionDeclaration.name}_l${i}_after")
                        emit("${functionDeclaration.name}_l${i}_nz:")
                        emit("movl $0, $targetLoc")
                        emit("${functionDeclaration.name}_l${i}_after:")
                    }
                }
                is Binop -> {
                    val (opB, opA) = popTo2Regs(opRequires8Bits(s.kind))
                    var resultRegister = opB

                    when (s.kind) {
                        Plus -> emit("add $opA, $opB")
                        Minus -> {
                            resultRegister = opA
                            emit("sub $opB, $opA")
                        }
                        Times -> emit("imul $opA, $opB")
                        Div -> {
                            resultRegister = "%eax"
                            emit("pushl %edx")
                            emit("movl $opA, %eax")
                            emit("movl $opB, %ebx")
                            emit("cltd")
                            emit("idiv %ebx")
                            emit("popl %edx")
                        }
                        Rem -> {
                            resultRegister = "%eax"
                            emit("pushl %edx")
                            emit("movl $opA, %eax")
                            emit("movl $opB, %ebx")
                            emit("cltd")
                            emit("idiv %ebx")
                            emit("movl %edx, %eax")
                            emit("popl %edx")
                        }
                        And, Or -> {
                            emit("and $opA, $opA")
                            emit("setnz ${lower8Bits(opA)}")
                            emit("and $1, $opA")
                            emit("and $opB, $opB")
                            emit("setnz ${lower8Bits(opB)}")
                            emit("and $1, $opB")
                            emit((if (s.kind == And) "and" else "or") + " $opA, $opB")
                        }
                        Eq, Neq, Gt, Lt, Leq, Geq -> {
                            emit("subl $opA, $opB")
                            emit("set${setComparisonOp[s.kind]!!} ${lower8Bits(opB)}")
                            emit("andl $1, $opB")
                        }
                    }
                    emitPush(resultRegister)
                }
                is Jmp -> {
                    symbolicStackAtInsn[s.nextInstruction] = Stack<SymbolicStackLocation>().apply { addAll(symbolicStack) }
                    insnCanContinue = false
                    emit("jmp ${functionDeclaration.name}_l${s.nextInstruction}")
                }
                is Jz -> {
                    val op = popToReg1()
                    symbolicStackAtInsn[s.nextInstruction] = Stack<SymbolicStackLocation>().apply { addAll(symbolicStack) }
                    emit("cmp $0, $op")
                    emit("jz ${functionDeclaration.name}_l${s.nextInstruction}")
                }
                is Call -> {
                    val fName = s.function.name
                    val existingProgramStackItemsCount = symbolicStack.filter { it == programStack }.count()
                    val lastTakenEbpOffset = existingProgramStackItemsCount * 4 + (localOffsets.values.min() ?: 0).absoluteValue
                    val regItemsCount = symbolicStack.size - existingProgramStackItemsCount
                    val programStackEbpOffsetBySymbolicStackIndex = symbolicStack.withIndex().associate { (index, stackItem) ->
                        val offset =
                            if (stackItem.isRegister) {
                                emit("pushl %${stackItem.name}")
                                -lastTakenEbpOffset - 4 * (1 + index)
                            } else {
                                (1 + index - regItemsCount) * -4
                            }
                        index to offset
                    }
                    val specialItemsForCalleeOnStack = if (s.function.canThrow) {
                        emit("pushl $0")
                        1
                    } else 0
                    val paramStackIndices = symbolicStack.indices.toList().takeLast(s.function.parameters.size).reversed()
                    for (paramStackIndex in paramStackIndices) {
                        emit("pushl ${programStackEbpOffsetBySymbolicStackIndex[paramStackIndex]}(%ebp)")
                    }
                    emit("call ${formatFunctionName(fName)}")
                    (s.function.parameters.size * 4).let { argBytesOnStack ->
                        if (argBytesOnStack > 0) emit("add \$${argBytesOnStack}, %esp")
                    }
                    if (s.function.canThrow) {
                        emit("pushl %eax")
                        emit("movl 4(%esp), %eax")
                        emit("cmp $0, %eax")
                        val labelWhenThrown = "whenThrown_${functionDeclaration.name}_$i"
                        val labelAfterCall = "afterCall_${functionDeclaration.name}_$i"
                        emit("jg $labelWhenThrown")
                        emit("jmp $labelAfterCall")
                        emit("$labelWhenThrown:")
                        emitPush("4(%esp)")
                        compileInstruction(i, St(thrownExceptionVariable))
                        emit("$labelAfterCall:")
                        emit("popl %eax")
                    }
                    (specialItemsForCalleeOnStack * 4).let { specialItemBytesOnStack ->
                        if (specialItemBytesOnStack > 0)
                            emit("add \$${specialItemBytesOnStack}, %esp")
                    }
                    for (loc in symbolicStack.filter { it.isRegister }.reversed()) {
                        emit("popl %${loc.name}")
                    }
                    s.function.parameters.forEach {
                        emitPop("%ebx")
                    }
                    emitPush("%eax")
                }
                TransEx -> {
                    if (functionDeclaration.name != "main") {
                        emitPush("${varOffsets[currentExceptionVariable]}(%ebp)")
                        val offsetBeyondParamters = 0
                        val parentFrameThrownExOffset = paramOffsets.values.max()?.plus(4) ?: paramsBoundary + offsetBeyondParamters
                        emitPop("$parentFrameThrownExOffset(%ebp)")
                    }
                }
                Ret0, Ret1 -> {
                    if (s == Ret1)
                        emitPop("%eax")
                    else
                        emit("movl $0, %eax")
                    emit("leave")
                    emit("ret")
                }
                Pop -> emitPop("%eax")
            }

            if (i + 1 in symbolicStackAtInsn && insnCanContinue) {
                check(symbolicStackAtInsn[i + 1] == symbolicStack)
            }
        }

        check(symbolicStack.isEmpty())
        for ((i, s) in source.withIndex()) {
            emit("${functionDeclaration.name}_l$i: # $s")
            compileInstruction(i, s)
        }
        check(symbolicStack.isEmpty())
    }

    override fun compile(source: StackProgram): String {
        val compilationEnvironment = CompilationEnvironment(source)
        with(compilationEnvironment) {
            emit(".text")

            for ((f, fCode) in source.functions) {
                compileFunction(f, fCode)
            }

            emit(".section .rodata")

            for ((i, l) in source.literalPool.withIndex()) {
                emit(formatPooledStringId(i) + ": .string \"${String(l)}\"")
            }
        }
        return compilationEnvironment.result.joinToString("\n") + "\n"
    }
}

private val setComparisonOp = mapOf(Eq to "z", Neq to "nz", Gt to "l", Lt to "g", Leq to "ge", Geq to "le")

private fun opRequires8Bits(binaryOperationKind: BinaryOperationKind) = when (binaryOperationKind) {
    And, Or, Eq, Neq, Gt, Lt, Leq, Geq -> true
    else -> false
}