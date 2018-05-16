package com.github.h0tk3y.compilersCourse.x86

import java.io.File
import java.nio.file.Files

fun assemble(asm: String, parentDir: File? = null, programName: String? = "output.exe"): File {
    val outputDir = parentDir ?: Files.createTempDirectory(null).toFile()
    val asmFile = File(outputDir, "$programName.s").apply { writeText(asm) }
    val exeFile = File(outputDir, "$programName")

    val runtimeFile = File("runtime/intrinsics.o")

    if (!runtimeFile.exists()) {
        val runtimeSourceFile = File(runtimeFile.parent, "intrinsics.c")
        if (!runtimeSourceFile.exists()) {
            throw RuntimeException("Could not find 'intrinsics.o' or 'intrinsics.c' in 'runtime' directory.")
        }
        val assembleRuntimeCmd =
            arrayOf("gcc", "-m32", "-std=gnu99", "-c", runtimeSourceFile.absolutePath, "-o", runtimeFile.absolutePath)
        Runtime.getRuntime().exec(assembleRuntimeCmd).run {
            waitFor()
            val log = inputStream.reader().readText()
            val errLog = errorStream.reader().readText()
            if (exitValue() != 0) {
                throw RuntimeException("GCC assembler failed to build intrinsics: \n\n" +
                                       "GCC output: \n$log\n\n" +
                                       "GCC error stream: \n$errLog")
            }
        }
    }

    val cmd = arrayOf("gcc", "-m32", runtimeFile.absolutePath, asmFile.absolutePath,
                      "-o", exeFile.absolutePath)
    Runtime.getRuntime().exec(cmd).run {
        waitFor()
        val log = inputStream.reader().readText()
        val errLog = errorStream.reader().readText()
        if (exitValue() != 0) {
            throw RuntimeException("GCC assembler failed to build program: \n\n" +
                                   "$asm\n\n" +
                                   "GCC output: \n$log\n\n" +
                                   "GCC error stream: \n$errLog")
        }
    }
    return exeFile
}

fun runExe(exe: File, input: List<String>): List<String>? {
    val cmd = arrayOf(exe.absolutePath)
    val exec = Runtime.getRuntime().exec(cmd)
    val stdin = exec.outputStream.writer()
    for (i in input) {
        stdin.write("$i\r\n")
    }
    stdin.close()
    val returnCode = exec.waitFor()
    val result = exec.inputStream.reader().readLines()
    if (returnCode != 0) {
        return null
    }
    return result
}

fun determinePlatform(): TargetPlatform {
    val os = System.getProperty("os.name").let {
        if (it.contains("win", true)) TargetPlatform.WIN else TargetPlatform.UNIX
    }
    return os
}