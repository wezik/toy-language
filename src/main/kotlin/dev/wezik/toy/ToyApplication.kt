package dev.wezik.toy

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess

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
        // NOTE: Reset the error handling flag within the interactive loop
        scannerContext.hadError = false
    }
}

fun runFile(filePath: String) {
    val bytes = File(filePath).readBytes()
    run(String(bytes))
    if (scannerContext.hadError) exitProcess(65)
}

fun run(source: String) {
    val tokens = scan(source)
    println("Tokens: ${tokens.joinToString(" ")}")

    val expr = parse(tokens)
    println("Expr: $expr")
}

// SCANNER

data class ScannerContext(var hadError: Boolean = false)

val scannerContext = ScannerContext()

fun handleError(line: Int, message: String, where: String = "") {
    System.err.println("[line $line]: Error $where: $message")
    scannerContext.hadError = true
}

data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any?,
    val line: Int,
) {
    override fun toString() = "#(type '$type' | lexeme '$lexeme' | literal '$literal')"
}

enum class TokenType {
    // Single char tokens
    L_PAREN, R_PAREN, L_BRACE, R_BRACE, L_BRACKET, R_BRACKET, COMMA, DOT, MINUS, PLUS, COLON, SEMICOLON, SLASH, STAR, QUESTION,

    // Compound colon tokens
    DOUBLE_COLON, COLON_EQUAL,

    // Custom sugar (nullable operators / lambdas)
    SAFE_CALL, ELVIS, ARROW,

    // Comparators
    BANG, BANG_EQUAL, EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,

    // Literals
    IDENTIFIER, STRING, INT_NUMBER, FLOAT_NUMBER,

    // Keywords
    TRUE, FALSE, NULL, IF, ELSE, FOR, RETURN, STRUCT, ENUM,

    EOF,
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
            while (peek()?.isDigit() == true) next()
        }

        val type = if (isFloating) TokenType.FLOAT_NUMBER else TokenType.INT_NUMBER
        val substr = source.substring(start, current)
        val value: Any = if (isFloating) substr.toDouble() else substr.toInt()

        register(type, value)
    }

    val keywords = mapOf(
        "true" to TokenType.TRUE,
        "false" to TokenType.FALSE,
        "null" to TokenType.NULL,
        "if" to TokenType.IF,
        "else" to TokenType.ELSE,
        "for" to TokenType.FOR,
        "return" to TokenType.RETURN,
        "struct" to TokenType.STRUCT,
        "enum" to TokenType.ENUM,
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
            '+' -> register(TokenType.PLUS)
            ':' -> register(
                when {
                    isNext(':') -> TokenType.DOUBLE_COLON
                    isNext('=') -> TokenType.COLON_EQUAL
                    else -> TokenType.COLON
                }
            )

            ';' -> register(TokenType.SEMICOLON)
            '*' -> register(TokenType.STAR)
            '?' -> register(
                when {
                    isNext('.') -> TokenType.SAFE_CALL
                    isNext(':') -> TokenType.ELVIS
                    else -> TokenType.QUESTION
                }
            )

            '-' -> register(if (isNext('>')) TokenType.ARROW else TokenType.MINUS)
            '!' -> register(if (isNext('=')) TokenType.BANG_EQUAL else TokenType.BANG)
            '=' -> register(if (isNext('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
            '<' -> register(if (isNext('=')) TokenType.LESS_EQUAL else TokenType.LESS)
            '>' -> register(if (isNext('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)
            '/' -> {
                if (!isNext('/')) register(TokenType.SLASH)
                else while (peek() != '\n' && current < source.length) current++
            }

            // white-space ignore
            ' ', '\r', '\t' -> {}

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

// PARSER
sealed interface Expr {
    data class Literal(val value: Any?) : Expr
    data class Grouping(val expr: Expr) : Expr
    data class Unary(val operator: Token, val right: Expr) : Expr
    data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr
}

fun parse(tokens: List<Token>): Expr {
    var current = 0

    fun peek(offset: Int = 0) = tokens[current + offset]
    fun isAtEnd() = peek().type == TokenType.EOF
    fun previous() = tokens[current - 1]
    fun check(type: TokenType) = if (isAtEnd()) false else peek().type == type

    fun next(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                next()
                return true
            }
        }

        return false
    }

    // I know... I just don't want to make it objective and scatter all around the place
    // it won't be null at the time of execution anyway
    var equality: (() -> Expr)? = null

    fun expression(): Expr {
        return equality!!.invoke()
    }

    fun consume(type: TokenType, message: String) {
        if (!check(type)) error(peek().toString() + message)
        next()
    }

    fun primary(): Expr {
        if (match(TokenType.FALSE)) return Expr.Literal(false)
        if (match(TokenType.TRUE)) return Expr.Literal(true)
        if (match(TokenType.NULL)) return Expr.Literal(null)

        if (match(TokenType.INT_NUMBER, TokenType.FLOAT_NUMBER, TokenType.STRING)) {
            return Expr.Literal(previous().literal)
        }

        if (match(TokenType.L_PAREN)) {
            val expr = expression()
            consume(TokenType.R_PAREN, "Expected ')' after expression.")
            return Expr.Grouping(expr)
        }

        error("failed to match primary token and fallen through all the cases")
    }

    fun unary(): Expr {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            val op = previous()
            val right = unary()
            return Expr.Unary(op, right)
        }

        return primary()
    }

    fun factor(): Expr {
        var expr = unary()

        while (match(TokenType.SLASH, TokenType.STAR)) {
            val op = previous()
            val right = unary()
            expr = Expr.Binary(expr, op, right)
        }

        return expr
    }

    fun term(): Expr {
        var expr = factor()

        while (match(TokenType.MINUS, TokenType.PLUS)) {
            val op = previous()
            val right = factor()
            expr = Expr.Binary(expr, op, right)
        }

        return expr
    }

    fun comparison(): Expr {
        var expr = term()

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            val op = previous()
            val right = term()
            expr = Expr.Binary(expr, op, right)
        }

        return expr
    }

    equality = {
        var expr = comparison()

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            val op = previous()
            val right = comparison()
            expr = Expr.Binary(expr, op, right)
        }

        expr
    }

    return expression()
}
