package com.github.h0tk3y.compilersCourse.jvm

import com.github.h0tk3y.compilersCourse.Compiler
import com.github.h0tk3y.compilersCourse.exhaustive
import com.github.h0tk3y.compilersCourse.language.*
import com.github.h0tk3y.compilersCourse.stack.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type.getDescriptor
import java.io.BufferedReader
import java.io.InputStream


class StackToJvmCompiler : Compiler<StackProgram, ByteArray> {
    private val brDescriptor = getDescriptor(BufferedReader::class.java)

    private fun compileIntrinsics(cw: ClassWriter) {
        cw.visitField(ACC_PRIVATE, "input", brDescriptor, null, null)
    }

    override fun compile(source: StackProgram): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "Program", null, "java/lang/Object", emptyArray())

        cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitVarInsn(ALOAD, 0)
            visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(RETURN)
            visitMaxs(-1, -1)
            visitEnd()
        }

        cw.visitMethod(ACC_PUBLIC, "<clinit>", "()V", null, null).apply {
            visitTypeInsn(NEW, "java/io/BufferedReader")
            visitInsn(DUP)
            visitTypeInsn(NEW, "java/io/InputStreamReader")
            visitInsn(DUP)
            visitFieldInsn(GETSTATIC, "java/lang/System", "in", getDescriptor(InputStream::class.java))
            visitMethodInsn(INVOKESPECIAL, "java/io/InputStreamReader", "<init>", "(Ljava/io/InputStream;)V", false)
            visitMethodInsn(INVOKESPECIAL, "java/io/BufferedReader", "<init>", "(Ljava/io/Reader;)V", false)
            visitFieldInsn(PUTSTATIC, "Program", "input", brDescriptor)
            visitInsn(RETURN)
            visitMaxs(-1, -1)
            visitEnd()
        }

        source.functions.forEach { (declaration, code) -> compileFunction(declaration, code, cw, declaration == source.entryPoint) }

        return cw.toByteArray()
    }

    fun f() {
        val a = false
        val b = !a
        println(b)
    }

    private fun compileFunction(declaration: FunctionDeclaration, source: List<StackStatement>, cw: ClassWriter, isMain: Boolean) {
        val signature = if (isMain)
            "([Ljava/lang/String;)V" else
            "(${(1..declaration.parameters.size).map { "I" }.joinToString("")})V"

        cw.visitMethod(ACC_PUBLIC + ACC_STATIC, declaration.name, signature, null, null).apply {
            val beginLabel = Label().apply { info = "begin" }
            val endLabel = Label().apply { info = "end" }
            visitLabel(beginLabel)

            val variables = collectVariables(source)
            val variablesMap = variables.withIndex().associate { (index, it) -> it to index + 2 }
            variablesMap.forEach { (v, index) -> visitLocalVariable(v.name, "I", null, beginLabel, endLabel, index) }

            val labels = (source + NOP).map { Label().apply { info = it; } }

            for ((index, s) in source.withIndex()) {
                visitLabel(labels[index])

                when (s) {
                    Nop -> visitInsn(NOP)

                    is Push -> visitLdcInsn(s.constant.value)
                    is Ld -> visitVarInsn(ILOAD, variablesMap[s.v]!!)
                    is St -> visitVarInsn(ISTORE, variablesMap[s.v]!!)
                    is Unop -> when (s.kind) {
                        Not -> {
                            val labelIfNz = Label()
                            val labelAfter = Label()
                            visitJumpInsn(IFNE, labelIfNz)
                            visitInsn(ICONST_0)
                            visitLabel(labelIfNz)
                            visitInsn(ICONST_1)
                            visitLabel(labelAfter)
                        }
                    }
                    is Binop -> when (s.kind) {
                        Plus -> visitInsn(IADD)
                        Minus -> visitInsn(ISUB)
                        Times -> visitInsn(IMUL)
                        Div -> visitInsn(IDIV)
                        Rem -> visitInsn(IREM)
                        And -> visitInsn(IAND)
                        Or -> visitInsn(IOR)
                        Eq, Neq, Gt, Lt, Leq, Geq -> {
                            val labelOtherwise = Label()
                            val labelAfter = Label()
                            visitJumpInsn(checkOtherwiseOp[s.kind]!!, labelOtherwise)
                            visitInsn(ICONST_1)
                            visitJumpInsn(GOTO, labelAfter)
                            visitLabel(labelOtherwise)
                            visitInsn(ICONST_0)
                            visitLabel(labelAfter)
                        }
                    }.exhaustive
                    is Jmp -> visitJumpInsn(GOTO, labels[s.nextInstruction])
                    is Jz -> {
                        visitInsn(ICONST_0)
                        visitJumpInsn(IF_ICMPEQ, labels[s.nextInstruction])
                    }
                    is Call -> when (s.function) {
                        is Intrinsic -> when (s.function) {
                            Intrinsic.READ -> {
                                visitFieldInsn(GETSTATIC, "Program", "input", brDescriptor)
                                visitMethodInsn(INVOKEVIRTUAL, "java/io/BufferedReader", "readLine", "()Ljava/lang/String;", false)
                                visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false)
                            }
                            Intrinsic.WRITE -> {
                                visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
                                visitInsn(SWAP)
                                visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false)
                            }
                            Intrinsic.STRMAKE -> TODO()
                            Intrinsic.STRCMP -> TODO()
                            Intrinsic.STRGET -> TODO()
                            Intrinsic.STRDUP -> TODO()
                            Intrinsic.STRSET -> TODO()
                            Intrinsic.STRCAT -> TODO()
                            Intrinsic.STRSUB -> TODO()
                            Intrinsic.STRLEN -> TODO()
                            Intrinsic.ARRMAKE -> TODO()
                            Intrinsic.ARRMAKEBOX -> TODO()
                            Intrinsic.ARRGET -> TODO()
                            Intrinsic.ARRSET -> TODO()
                            Intrinsic.ARRLEN -> TODO()
                        }
                        else -> TODO()
                    }
                    Ret0 -> TODO()
                    Ret1 -> TODO()
                    Pop -> visitInsn(POP)
                    is PushPooled -> TODO()
                    TransEx -> TODO()
                }.exhaustive
            }

            visitLabel(labels.last())
            visitInsn(RETURN)
            visitMaxs(-1, -1)
            visitEnd()
        }
    }

    val checkOtherwiseOp = mapOf(Eq to IF_ICMPNE,
                                 Neq to IF_ICMPEQ,
                                 Gt to IF_ICMPLE,
                                 Lt to IF_ICMPGE,
                                 Geq to IF_ICMPLT,
                                 Leq to IF_ICMPGT)
}