package dev.wezik.toy

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) runInteractive() else runFile(args[0])
}

data class Context(var hadError: Boolean = false)

val context = Context()

fun runInteractive() {
    val input = InputStreamReader(System.`in`)
    val reader = BufferedReader(input)

    fun BufferedReader.readWithPrompt(): String? {
        print("> ")
        return reader.readLine()
    }

    while (true) {
        val line = reader.readWithPrompt() ?: break
        run(line)
        // NOTE: Reset the error handling flag within the interactive loop
        context.hadError = false
    }
}

fun runFile(filePath: String) {
    val bytes = File(filePath).readBytes()
    run(String(bytes))
}

fun handleError(line: Int, message: String, where: String = "") {
    System.err.println("[line $line]: Error $where: $message")
    context.hadError = true
}

data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any?,
    val line: Int,
) {
    override fun toString() = "$type $lexeme $literal"
}

enum class TokenType {
    // Single char tokens
    L_PAREN, R_PAREN, L_BRACE, R_BRACE, L_BRACKET, R_BRACKET, COMMA, DOT, MINUS, PLUS, COLON, SEMICOLON, SLASH, STAR,

    // Comparators
    BANG, BANG_EQUAL, EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,

    // Literals
    IDENTIFIER, STRING, NUMBER,

    EOF,
}

fun run(source: String) {
    val tokens = scan(source)
    println(tokens.joinToString(" "))
    if (context.hadError) exitProcess(65)
}

fun scan(source: String): List<Token> {
    val tokens = mutableListOf<Token>()

    var current = 0
    var start = 0
    var line = 1

    fun next() = source[current++]
    fun register(type: TokenType, literal: Any? = null) {
        val str = source.substring(start, current)
        val token = Token(type, str, literal, line)
        tokens.add(token)
    }

    val scanToken = fun() {
        when (val c = next()) {
            '(' -> register(TokenType.L_PAREN)
            ')' -> register(TokenType.R_PAREN)
            '{' -> register(TokenType.L_BRACE)
            '}' -> register(TokenType.R_BRACE)
            '[' -> register(TokenType.L_BRACKET)
            ']' -> register(TokenType.R_BRACKET)
            ',' -> register(TokenType.COMMA)
            '.' -> register(TokenType.DOT)
            '-' -> register(TokenType.MINUS)
            '+' -> register(TokenType.PLUS)
            ':' -> register(TokenType.COLON)
            ';' -> register(TokenType.SEMICOLON)
            '*' -> register(TokenType.STAR)
            else -> handleError(line, "Unexpected character '$c'")
        }
    }

    while (current < source.length) {
        start = current
        scanToken()
    }

    tokens.add(Token(TokenType.EOF, "", null, line))
    return tokens
}