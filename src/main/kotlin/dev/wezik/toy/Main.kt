package dev.wezik.toy

import dev.wezik.toy.interpreter.Environment
import dev.wezik.toy.interpreter.InterpretResult
import dev.wezik.toy.interpreter.NativeFunction
import dev.wezik.toy.interpreter.interpret
import dev.wezik.toy.lexer.Token
import dev.wezik.toy.lexer.scan
import dev.wezik.toy.parser.ParseResult
import dev.wezik.toy.parser.parse
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

fun main(args: Array<String>) {
    val env = Environment().apply {
        declare("print", NativeFunction(1) { args -> println(args[0] ?: "null"); null }, false)
    }

    if (args.isEmpty()) runInteractive(env) else runFile(args[0], env)
}

fun runInteractive(env: Environment) {
    val input = InputStreamReader(System.`in`)
    val reader = BufferedReader(input)

    fun BufferedReader.readWithPrompt(): String? {
        print("> ")
        return readLine()
    }

    while (true) {
        val line = reader.readWithPrompt() ?: break
        run(line, env)
    }
}

fun runFile(filePath: String, env: Environment) = run(File(filePath).readText(), env)

fun run(source: String, env: Environment) {
    val stmts = when (val result = parse(scan(source))) {
        is ParseResult.Ok -> result.stmts
        is ParseResult.Error -> {
            result.errors.forEach { report(it.token, it.message) }
            return
        }
    }

    val result = interpret(stmts, env)
    if (result is InterpretResult.Error) {
        result.errors.forEach { report(it.token, it.message) }
    }
}

fun report(token: Token?, message: String) {
    if (token == null || token.type == Token.Type.EOF) {
        System.err.println("[line ${token?.line ?: "?"}] at the end: $message")
    } else {
        System.err.println("[line ${token.line}:${token.column}] at ${token.text}: $message")
    }
}
