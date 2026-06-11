package dev.wezik.toy

import dev.wezik.toy.interpreter.EvalResult
import dev.wezik.toy.interpreter.interpret
import dev.wezik.toy.lexer.Token
import dev.wezik.toy.lexer.scan
import dev.wezik.toy.parser.ParseResult
import dev.wezik.toy.parser.parse
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
        return readLine()
    }

    while (true) {
        val line = reader.readWithPrompt() ?: break
        run(line)
    }
}

fun runFile(filePath: String) {
    val bytes = File(filePath).readBytes()
    run(String(bytes))
    // if (scannerContext.hadError) exitProcess(65)
}

fun run(source: String) {
    val tokens = scan(source)
    println("Tokens: ${tokens.joinToString(" ")}")

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
    println("Expr: ${parseResult.expr}")

    val evalResult = interpret(parseResult.expr)
    if (evalResult is EvalResult.Error) {
        for (e in evalResult.errors) {
            if (e.token?.type == Token.Type.EOF) {
                System.err.println("[line ${e.token.line}] at the end: ${e.message}")
            } else {
                System.err.println("[line ${e.token?.line}:${e.token?.column}] at ${e.token?.text}: ${e.message}")
            }
        }
        return
    }

    // force the type
    if (evalResult !is EvalResult.Ok) error("???")
    println("Result: ${evalResult.value ?: "null"}")
}
