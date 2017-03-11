package com.github.h0tk3y.compilersCourse.jvm

import com.github.h0tk3y.compilersCourse.Compiler
import com.github.h0tk3y.compilersCourse.language.*
import com.github.h0tk3y.compilersCourse.stack.*
import jdk.internal.org.objectweb.asm.ClassWriter
import jdk.internal.org.objectweb.asm.Label
import jdk.internal.org.objectweb.asm.Opcodes.*
import jdk.internal.org.objectweb.asm.Type
import java.io.InputStream


class StackToJvmCompiler : Compiler<List<StackStatement>, ByteArray> {
    override fun compile(source: List<StackStatement>): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)

        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "Program", null, "java/lang/Object", emptyArray())

        val ctor = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitMaxs(10, 5)
        ctor.visitVarInsn(ALOAD, 0)
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(RETURN)
        ctor.visitMaxs(-1, -1)
        ctor.visitEnd()

        val mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null)
        ctor.visitMaxs(10, 5)
        val beginLabel = Label().apply { info = "begin" }
        val endLabel = Label().apply { info = "end" }
        mv.visitLabel(beginLabel)

        val inputLocalVarIndex = 1
        mv.visitLocalVariable("input", "Ljava/io/BufferedReader;", null, beginLabel, endLabel, inputLocalVarIndex)
        mv.visitTypeInsn(NEW, "java/io/BufferedReader")
        mv.visitInsn(DUP)
        mv.visitTypeInsn(NEW, "java/io/InputStreamReader")
        mv.visitInsn(DUP)
        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "in", Type.getDescriptor(InputStream::class.java))
        mv.visitMethodInsn(INVOKESPECIAL, "java/io/InputStreamReader", "<init>", "(Ljava/io/InputStream;)V", false)
        mv.visitMethodInsn(INVOKESPECIAL, "java/io/BufferedReader", "<init>", "(Ljava/io/Reader;)V", false)
        mv.visitVarInsn(ASTORE, inputLocalVarIndex)

        val variables = collectVariables(source)
        val variablesMap = variables.withIndex().associate { (index, it) -> it to index + 2 }
        variablesMap.forEach { (v, index) -> mv.visitLocalVariable(v.name, "I", null, beginLabel, endLabel, index) }

        val labels = (source + NOP).map { Label().apply { info = it; } }

        for ((index, s) in source.withIndex()) {
            mv.visitLabel(labels[index])

            when (s) {
                Nop -> mv.visitInsn(NOP)
                Rd -> {
                    mv.visitVarInsn(ALOAD, inputLocalVarIndex)
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/BufferedReader", "readLine", "()Ljava/lang/String;", false)
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false)
                }
                Wr -> {
                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
                    mv.visitInsn(SWAP)
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false)
                }

                is Push -> mv.visitLdcInsn(s.constant.value)
                is Ld -> mv.visitVarInsn(ILOAD, variablesMap[s.v]!!)
                is St -> mv.visitVarInsn(ISTORE, variablesMap[s.v]!!)
                is Unop -> when (s.kind) {
                    Not -> mv.visitInsn(INEG)
                }
                is Binop -> when (s.kind) {
                    Plus -> mv.visitInsn(IADD)
                    Minus -> mv.visitInsn(ISUB)
                    Times -> mv.visitInsn(IMUL)
                    Div -> mv.visitInsn(IDIV)
                    Rem -> mv.visitInsn(IREM)
                    And -> mv.visitInsn(IAND)
                    Or -> mv.visitInsn(IOR)
                    Eq -> {
                        val labelNeq = Label()
                        val labelAfter = Label()
                        mv.visitJumpInsn(IF_ICMPNE, labelNeq)
                        mv.visitInsn(ICONST_1)
                        mv.visitJumpInsn(GOTO, labelAfter)
                        mv.visitLabel(labelNeq)
                        mv.visitInsn(ICONST_0)
                        mv.visitLabel(labelAfter)
                    }
                    Neq -> {
                        val labelEq = Label()
                        val labelAfter = Label()
                        mv.visitJumpInsn(IF_ICMPEQ, labelEq)
                        mv.visitInsn(ICONST_1)
                        mv.visitJumpInsn(GOTO, labelAfter)
                        mv.visitLabel(labelEq)
                        mv.visitInsn(ICONST_0)
                        mv.visitLabel(labelAfter)
                    }
                    Gt -> {
                        val labelLe = Label()
                        val labelAfter = Label()
                        mv.visitJumpInsn(IF_ICMPLE, labelLe)
                        mv.visitInsn(ICONST_1)
                        mv.visitJumpInsn(GOTO, labelAfter)
                        mv.visitLabel(labelLe)
                        mv.visitInsn(ICONST_0)
                        mv.visitLabel(labelAfter)
                    }
                    Lt -> {
                        val labelGe = Label()
                        val labelAfter = Label()
                        mv.visitJumpInsn(IF_ICMPGE, labelGe)
                        mv.visitInsn(ICONST_1)
                        mv.visitJumpInsn(GOTO, labelAfter)
                        mv.visitLabel(labelGe)
                        mv.visitInsn(ICONST_0)
                        mv.visitLabel(labelAfter)
                    }
                }
                is Jmp -> mv.visitJumpInsn(GOTO, labels[s.nextInstruction])
                is Jz -> {
                    mv.visitInsn(ICONST_0)
                    mv.visitJumpInsn(IF_ICMPEQ, labels[s.nextInstruction])
                }
            }
        }

        mv.visitLabel(labels.last())
        mv.visitInsn(RETURN)
        mv.visitMaxs(-1, -1)
        mv.visitEnd()

        return cw.toByteArray()
    }
}