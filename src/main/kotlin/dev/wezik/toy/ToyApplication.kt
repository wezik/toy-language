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
    if (context.hadError) exitProcess(65)
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
    override fun toString() = "Token(type '$type', lexeme '$lexeme', literal '$literal')"
}

enum class TokenType {
    // Single char tokens
    L_PAREN, R_PAREN, L_BRACE, R_BRACE, L_BRACKET, R_BRACKET, COMMA, DOT, MINUS, PLUS, COLON, SEMICOLON, SLASH, STAR,

    // Comparators
    BANG, BANG_EQUAL, EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,

    // Literals
    IDENTIFIER, STRING, INT_NUMBER, FLOAT_NUMBER,

    // Keywords
    TRUE, FALSE, IF, ELSE, FOR,

    EOF,
}

fun run(source: String) {
    val tokens = scan(source)
    println(tokens.joinToString(" "))
}

fun scan(source: String): List<Token> {
    val tokens = mutableListOf<Token>()

    var current = 0
    var start = 0
    var line = 1

    fun peek(offset: Int = 0) = source.getOrNull(current + offset)

    fun next() = source[current++]

    fun isNext(expected: Char): Boolean {
        val c = source.getOrNull(current)
        val isExpected = c == expected
        if (isExpected) current++
        return isExpected
    }

    fun register(type: TokenType, literal: Any? = null) {
        val str = source.substring(start, current)
        val token = Token(type, str, literal, line)
        tokens.add(token)
    }

    fun scanString() {
        while (peek() != '"' && current < source.length) {
            // explicit handling as we can capture lines within the string
            if (peek() == '\n') line++
            next()
        }

        if (current >= source.length) {
            handleError(line, "Unterminated string.")
            return
        }

        next()
        val value = source.substring(start + 1, current - 1) // trim quotes
        register(TokenType.STRING, value)
    }

    fun scanNumber() {
        while (peek()?.isDigit() == true) next()

        var isFloating = false
        if (peek() == '.' && peek(offset = 1)?.isDigit() == true) {
            isFloating = true
            next() // consume '.'
            while(peek()?.isDigit() == true) next()
        }

        val type = if (isFloating) TokenType.FLOAT_NUMBER else TokenType.INT_NUMBER
        val substr = source.substring(start, current)
        val value: Any = if (isFloating) substr.toDouble() else substr.toInt()

        register(type, value)
    }

    val keywords = mapOf(
        "true" to TokenType.TRUE,
        "false" to TokenType.FALSE,
        "for" to TokenType.FOR,
        "if" to TokenType.IF,
        "else" to TokenType.ELSE,
    )

    fun scanIdentifier() {
        while (peek()?.isLetterOrDigit() == true || peek() == '_') next()
        val substr = source.substring(start, current)
        register(keywords[substr] ?: TokenType.IDENTIFIER)
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
            '!' -> register(if (isNext('=')) TokenType.BANG_EQUAL else TokenType.BANG)
            '=' -> register(if (isNext('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
            '<' -> register(if (isNext('=')) TokenType.LESS_EQUAL else TokenType.LESS)
            '>' -> register(if (isNext('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)
            '/' -> {
                if (!isNext('/')) register(TokenType.SLASH)
                else while (peek() != '\n' && current < source.length) current++
            }
            ' ', '\r', '\t' -> { /* Ignore */ }
            '\n' -> line++
            '"' -> scanString()

            else -> when {
                c.isDigit() -> scanNumber()
                c.isLetter() || c == '_' -> scanIdentifier()
                else -> handleError(line, "Unexpected character '$c'")
            }
        }
    }

    while (current < source.length) {
        start = current
        scanToken()
    }

    tokens.add(Token(TokenType.EOF, "", null, line))
    return tokens
}