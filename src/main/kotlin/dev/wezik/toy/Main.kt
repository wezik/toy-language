package dev.wezik.toy

import dev.wezik.toy.checker.Type
import dev.wezik.toy.checker.TypeEnv
import dev.wezik.toy.checker.typeCheck
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

    val typeEnv = TypeEnv().apply {
        declare("print", Type.FunctionT(listOf(Type.AnyT), Type.NullT))
    }

    if (args.isEmpty()) runInteractive(env, typeEnv) else runFile(args[0], env, typeEnv)
}

fun runInteractive(env: Environment, typeEnv: TypeEnv) {
    val input = InputStreamReader(System.`in`)
    val reader = BufferedReader(input)

    fun BufferedReader.readWithPrompt(): String? {
        print("> ")
        return readLine()
    }

    while (true) {
        val line = reader.readWithPrompt() ?: break
        run(line, env, typeEnv)
    }
}

fun runFile(filePath: String, env: Environment, typeEnv: TypeEnv) = run(File(filePath).readText(), env, typeEnv)

fun run(source: String, env: Environment, typeEnv: TypeEnv) {
    val stmts = when (val result = parse(scan(source))) {
        is ParseResult.Ok -> result.stmts
        is ParseResult.Error -> {
            result.errors.forEach { report(it.token, it.message) }
            return
        }
    }

    val typeErrors = typeCheck(stmts, typeEnv)
    typeErrors.forEach { report(it.name, it.message) }
    if (typeErrors.isNotEmpty()) return

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
