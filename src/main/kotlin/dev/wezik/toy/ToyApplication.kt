package dev.wezik.toy

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

fun main(args: Array<String>) {
    if (args.isEmpty()) runInteractive() else runFile(args[0])
}

fun runInteractive() {
    val input = InputStreamReader(System.`in`)
    val reader = BufferedReader(input)

    fun BufferedReader.readWithPrompt(): String? {
        print("> ")
        return reader.readLine()
    }

    while (true) {
        val line = reader.readWithPrompt() ?: break
        println("Interpreting: $line")
    }
}

fun runFile(filePath: String) {
    val bytes = File(filePath).readBytes()
    println(String(bytes))
}