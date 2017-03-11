package com.github.h0tk3y.compilersCourse.x86

import com.github.h0tk3y.compilersCourse.Compiler
import com.github.h0tk3y.compilersCourse.language.*
import com.github.h0tk3y.compilersCourse.stack.*

class StackToX86Compiler : Compiler<List<StackStatement>, String> {
    override fun compile(source: List<StackStatement>): String {
        val result = StringBuilder()
        fun emit(s: String) = result.append("$s\n")

        emit(".text")

        val variables = collectVariables(source)
        for (v in variables) {
            emit(".comm ${v.name}, 4, 4")
        }

        emit(".globl main")

        emit("main:")

        for ((i, s) in (source + Nop).withIndex()) {
            emit("l$i:")

            when (s) {
                Nop -> Unit
                Rd -> {
                    emit("call read")
                    emit("pushl %eax")
                }
                Wr -> {
                    emit("call write")
                    emit("popl %eax")
                }
                is Push -> emit("pushl \$${s.constant.value}")
                is Ld -> emit("pushl ${s.v.name}")
                is St -> emit("popl ${s.v.name}")
                is Unop -> when (s.kind) {
                    Not -> {
                        emit("popl %eax")
                        emit("neg %eax")
                        emit("pushl %eax")
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
                            emit("xor %edx, %edx")
                            emit("idiv %ebx")
                        }
                        Rem -> {
                            resultRegister = "%edx"
                            emit("xor %edx, %edx")
                            emit("idiv %ebx")
                        }
                        And -> emit("and %eax, %ebx")
                        Or -> emit("or %eax, %ebx")
                        Eq -> {
                            emit("sub %eax, %ebx")
                            emit("setz %bl")
                            emit("and $1, %ebx")
                        }
                        Neq -> {
                            emit("sub %eax, %ebx")
                            emit("setnz %bl")
                            emit("and $1, %ebx")
                        }
                        Gt -> {
                            emit("sub %ebx, %eax")
                            emit("setg %bl")
                            emit("and $1, %ebx")
                        }
                        Lt -> {
                            emit("sub %eax, %ebx")
                            emit("setl %bl")
                            emit("and $1, %ebx")
                        }
                    }
                    emit("pushl $resultRegister")
                }
                is Jmp -> emit("jmp l${s.nextInstruction}")
                is Jz -> {
                    emit("popl %eax")
                    emit("cmp $0, %eax")

                    emit("jz l${s.nextInstruction}")
                }
            }
        }

        emit("xor %eax, %eax")

        return result.toString()
    }
}