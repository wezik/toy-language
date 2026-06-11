package dev.wezik.toy

import dev.wezik.toy.interpreter.Environment
import dev.wezik.toy.interpreter.InterpretResult
import dev.wezik.toy.interpreter.interpret
import dev.wezik.toy.lexer.Token
import dev.wezik.toy.lexer.scan
import dev.wezik.toy.parser.ParseResult
import dev.wezik.toy.parser.parse
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

fun main(args: Array<String>) {
    val env = Environment()
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

fun runFile(filePath: String, env: Environment) {
    val bytes = File(filePath).readBytes()
    run(String(bytes), env)
    // if (scannerContext.hadError) exitProcess(65)
}

fun run(source: String, env: Environment) {
    // tokenize
    val tokens = scan(source)
    // println("Tokens: ${tokens.joinToString(" ")}")

    // parse
    val parseResult = parse(tokens)
    if (parseResult is ParseResult.Error) {
        for (e in parseResult.errors) {
            if (e.token?.type == Token.Type.EOF) {
                System.err.println("[line ${e.token.line}] at the end: ${e.message}")
            } else {
                System.err.println("[line ${e.token?.line}:${e.token?.column}] at ${e.token?.text}: ${e.message}")
            }
        }
        return
    }

    // force the type
    if (parseResult !is ParseResult.Ok) error("???")
    // println("Expr: ${parseResult.expr}")

    // interpret
    val interpretResult = interpret(parseResult.stmts, env)
    if (interpretResult is InterpretResult.Error) {
        for (e in interpretResult.errors) {
            if (e.token?.type == Token.Type.EOF) {
                System.err.println("[line ${e.token.line}] at the end: ${e.message}")
            } else {
                System.err.println("[line ${e.token?.line}:${e.token?.column}] at ${e.token?.text}: ${e.message}")
            }
        }
        return
    }

    // force the type
    if (interpretResult !is InterpretResult.Ok) error("???")
}
