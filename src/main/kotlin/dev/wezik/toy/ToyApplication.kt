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
    println(tokens.joinToString(" "))
    val expr = Expr.Grouping(
        expr = Expr.Binary(
            left = Expr.Literal(2),
            operator = Token(TokenType.PLUS, "+", null, 1),
            right = Expr.Grouping(
                expr = Expr.Binary(
                    left = Expr.Literal(3),
                    operator = Token(TokenType.STAR, "*", null, 1),
                    right = Expr.Grouping(
                        expr = Expr.Literal(5),
                    ),
                )
            ),
        )
    )
    println(prettyPrint(expr))
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
            ':' -> register(when {
                isNext(':') -> TokenType.DOUBLE_COLON
                isNext('=') -> TokenType.COLON_EQUAL
                else -> TokenType.COLON
            })
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

interface PrintTask
data class Evaluate(val expr: Expr) : PrintTask
data class Append(val text: String) : PrintTask

fun prettyPrint(root: Expr): String {
    val printTasks = ArrayDeque<PrintTask>()
    val output = StringBuilder()
    printTasks.addLast(Evaluate(root))

    // Tree traversal using an explicit stack instead of recursion, to avoid stack overflow
    // on deeply nested expressions. Each iteration either appends text directly or breaks
    // an expression into smaller tasks pushed back onto the stack.
    // Tasks are pushed in reverse execution order since the stack is LIFO.
    while (printTasks.isNotEmpty()) {
        when (val task = printTasks.removeLast()) {
            is Append -> output.append(task.text)
            is Evaluate -> when (val expr = task.expr) {
                is Expr.Literal -> output.append(expr.value?.toString() ?: "null")
                is Expr.Grouping -> {
                    printTasks.addLast(Append(")"))
                    printTasks.addLast(Evaluate(expr.expr))
                    printTasks.addLast(Append("(group "))
                }

                is Expr.Unary -> {
                    printTasks.addLast(Append(")"))
                    printTasks.addLast(Evaluate(expr.right))
                    printTasks.addLast(Append("(${expr.operator.lexeme} "))
                }

                is Expr.Binary -> {
                    printTasks.addLast(Append(")"))
                    printTasks.addLast(Evaluate(expr.right))
                    printTasks.addLast(Append(" "))
                    printTasks.addLast(Evaluate(expr.left))
                    printTasks.addLast(Append("(${expr.operator.lexeme} "))
                }
            }
        }
    }

    return output.toString()
}