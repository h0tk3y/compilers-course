package com.github.h0tk3y.compilersCourse.x86

import com.github.h0tk3y.compilersCourse.Compiler
import com.github.h0tk3y.compilersCourse.exhaustive
import com.github.h0tk3y.compilersCourse.language.*
import com.github.h0tk3y.compilersCourse.stack.*

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
    }

    private fun CompilationEnvironment.compileFunction(functionDeclaration: FunctionDeclaration, source: List<StackStatement>) {
        functionDeclaration.name.let { fName ->
            emit(".globl ${formatFunctionName(fName)}")
            emit("${formatFunctionName(fName)}:")
        }

        emit("pushl %ebp")
        emit("movl %esp, %ebp")

        val functionScope = (collectVariables(source)).distinct()
        val locals = functionScope - functionDeclaration.parameters
        val localOffsets = locals.asSequence().zip(generateSequence(-4) { it - 4 }).toMap()
        val paramsBoundary = 8
        val paramOffsets = functionDeclaration.parameters.asSequence().zip(generateSequence(paramsBoundary) { it + 4 }).toMap()
        locals.forEach { emit("pushl $0") }

        val varOffsets = localOffsets + paramOffsets

        fun compileInstruction(i: Int, s: StackStatement): Unit =
            when (s) {
                Nop -> Unit
                is Push -> emit("pushl \$${s.constant.value}")
                is PushPooled -> emit("pushl \$" + formatPooledStringId(s.id))
                is Ld -> {
                    emit("pushl ${varOffsets[s.v]}(%ebp)")
                }
                is St -> emit("popl ${varOffsets[s.v]}(%ebp)")
                is Unop -> when (s.kind) {
                    Not -> {
                        emit("popl %eax")
                        emit("cmp $0, %eax")
                        emit("jnz ${functionDeclaration.name}_l${i}_nz")
                        emit("pushl $1")
                        emit("jmp ${functionDeclaration.name}_l${i}_after")
                        emit("${functionDeclaration.name}_l${i}_nz:")
                        emit("pushl $0")
                        emit("${functionDeclaration.name}_l${i}_after:")
                    }
                }
                is Binop -> {
                    emit("popl %ebx")
                    emit("popl %eax")
                    var resultRegister = "%ebx"
                    when (s.kind) {
                        Plus -> emit("add %eax, %ebx")
                        Minus -> {
                            resultRegister = "%eax"
                            emit("sub %ebx, %eax")
                        }
                        Times -> emit("imul %eax, %ebx")
                        Div -> {
                            resultRegister = "%eax"
                            emit("cltd")
                            emit("idiv %ebx")
                        }
                        Rem -> {
                            resultRegister = "%edx"
                            emit("cltd")
                            emit("idiv %ebx")
                        }
                        And, Or -> {
                            emit("and %eax, %eax")
                            emit("setnz %al")
                            emit("and $1, %eax")
                            emit("and %ebx, %ebx")
                            emit("setnz %bl")
                            emit("and $1, %ebx")
                            emit((if (s.kind == And) "and" else "or") + " %eax, %ebx")
                        }
                        Eq, Neq, Gt, Lt, Leq, Geq -> {
                            emit("subl %eax, %ebx")
                            emit("set${setComparisonOp[s.kind]!!} %bl")
                            emit("andl $1, %ebx")
                        }
                    }
                    emit("pushl $resultRegister")
                }
                is Jmp -> emit("jmp ${functionDeclaration.name}_l${s.nextInstruction}")
                is Jz -> {
                    emit("popl %eax")
                    emit("cmp $0, %eax")
                    emit("jz ${functionDeclaration.name}_l${s.nextInstruction}")
                }
                is Call -> {
                    val fName = s.function.name
                    //todo save registers and change this offset
                    val lastArgOffset = 4
                    val offsetShift = 8
                    emit("pushl $0")
                    for (j in s.function.parameters.indices) {
                        emit("pushl ${lastArgOffset + offsetShift * j}(%esp)")
                    }
                    emit("call ${formatFunctionName(fName)}")
                    //todo restore registers and change + 0
                    emit("add \$${s.function.parameters.size * 4}, %esp")
                    if (s.function.canThrow) {
                        emit("pushl %eax")
                        emit("movl 4(%esp), %eax")
                        emit("cmp $0, %eax")
                        val labelWhenThrown = "whenThrown_${functionDeclaration.name}_$i"
                        val labelAfterCall = "afterCall_${functionDeclaration.name}_$i"
                        emit("jg $labelWhenThrown")
                        emit("jmp $labelAfterCall")
                        emit("$labelWhenThrown:")
                        emit("pushl 4(%esp)")
                        compileInstruction(i, St(thrownExceptionVariable))
                        emit("$labelAfterCall:")
                        emit("popl %eax")
                    }
                    emit("add \$${lastArgOffset + s.function.parameters.size * 4}, %esp")
                    emit("pushl %eax")
                }
                TransEx -> run {
                    if (functionDeclaration.name != "main") {
                        compileInstruction(i, Ld(currentExceptionVariable))
                        val offsetBeyondParamters = 0
                        val parentFrameThrownExOffset = paramOffsets.values.max()?.plus(4) ?: paramsBoundary + offsetBeyondParamters
                        emit("popl $parentFrameThrownExOffset(%ebp)")
                    }
                }
                Ret0, Ret1 -> {
                    emit(if (s == Ret1) "popl %eax" else "movl $0, %eax")
                    emit("leave")
                    emit("ret")
                }
                Pop -> emit("popl %eax")
            }.exhaustive

        for ((i, s) in (source + Ret0).withIndex()) {
            emit("${functionDeclaration.name}_l$i: # $s")
            compileInstruction(i, s)
        }
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