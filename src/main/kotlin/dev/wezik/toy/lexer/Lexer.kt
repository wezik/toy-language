package dev.wezik.toy.lexer

import dev.wezik.toy.lexer.Token.Literal
import dev.wezik.toy.lexer.Token.Type.*

private class SourceContext(val source: String) {
    var line = 1
    var current = 0
    var column = 1

    fun peek(offset: Int = 0) = source.getOrNull(current + offset)
    fun advance() = source[current++].also {
        if (it == '\n') {
            line++
            column = 1
        } else column++
    }

    fun isAtEnd() = current >= source.length
}

private fun Char.isAlpha() = this.isLetter() || this == '_'
private fun Char.isAlphaNumeric() = this.isLetterOrDigit() || this == '_'

fun scan(source: String): List<Token> {
    val ctx = SourceContext(source)
    var start = 0
    var startColumn = 1

    val tokens = mutableListOf<Token>()
    fun tokenize(type: Token.Type, literal: Literal = Literal.None) {
        val text = ctx.source.substring(start, ctx.current)
        val token = Token(type, text, literal, ctx.line, startColumn)
        tokens.add(token)
    }

    fun handleError(line: Int, message: String, where: String = "") {
        System.err.println("[line $line]: Error $where: $message")
    }

    fun match(vararg chars: Char): Boolean {
        val next = ctx.peek(0) ?: return false
        return if (next in chars) {
            ctx.advance()
            true
        } else false
    }

    fun colonScanner() = tokenize(
        when {
            match(':') -> DOUBLE_COLON
            match('=') -> COLON_EQUAL
            else -> COLON
        }
    )

    fun questionScanner() = tokenize(
        when {
            match(':') -> QUESTION_COLON
            match('.') -> QUESTION_DOT
            else -> QUESTION
        }
    )

    fun slashScanner() {
        // line comment handling
        if (match('/')) {
            while (ctx.peek() != '\n' && !ctx.isAtEnd()) {
                ctx.advance()
            }
            return
        }

        // multi-line comment handling
        if (match('*')) {
            while (!ctx.isAtEnd()) {
                if (ctx.peek() == '*' && ctx.peek(1) == '/') {
                    // consume '*/'
                    ctx.advance()
                    ctx.advance()
                    return
                }
                ctx.advance()
            }
            // unterminated comment
            return
        }

        tokenize(SLASH)
    }

    fun doubleQuoteScanner() {
        while (ctx.peek() != '"' && !ctx.isAtEnd()) ctx.advance()
        if (ctx.isAtEnd()) handleError(ctx.line, "Unterminated string.")

        ctx.advance() // consume '"'
        val text = ctx.source.substring(start + 1, ctx.current - 1) // trim '"'
        tokenize(STRING, Literal.StringValue(text))
    }

    fun digitScanner() {
        while (ctx.peek()?.isDigit() == true || ctx.peek() == '_') ctx.advance()

        var integer = true
        if (ctx.peek() == '.' && ctx.peek(offset = 1)?.isDigit() == true) {
            integer = false
            ctx.advance() // consume '.'
            while (ctx.peek()?.isDigit() == true || ctx.peek() == '_') ctx.advance()
        }

        val text = ctx.source.substring(start, ctx.current).replace("_", "")
        val typeLiteral = if (integer) {
            INT to Literal.IntValue(text.toInt())
        } else {
            DOUBLE to Literal.DoubleValue(text.toDouble())
        }

        typeLiteral.let { (type, literal) -> tokenize(type, literal) }
    }

    fun identifierScanner() {
        while (ctx.peek()?.isAlphaNumeric() == true) ctx.advance()
        val text = ctx.source.substring(start, ctx.current)
        val type = when (text) {
            "else" -> ELSE
            "enum" -> ENUM
            "false" -> FALSE
            "for" -> FOR
            "if" -> IF
            "null" -> NULL
            "return" -> RETURN
            "struct" -> STRUCT
            "true" -> TRUE
            "while" -> WHILE
            "print" -> PRINT // TODO: remove once native function calls are supported
            else -> IDENTIFIER
        }
        tokenize(type)
    }

    while (!ctx.isAtEnd()) {
        start = ctx.current
        startColumn = ctx.column + 1 // +1  because it is used in non-index based reporting

        when (val c = ctx.advance()) {
            ' ', '\r', '\t', ';', '\n' -> {} // ignore whitespace and semicolons
            '(' -> tokenize(L_PAREN)
            ')' -> tokenize(R_PAREN)
            '{' -> tokenize(L_BRACE)
            '}' -> tokenize(R_BRACE)
            '[' -> tokenize(L_BRACKET)
            ']' -> tokenize(R_BRACKET)
            ',' -> tokenize(COMMA)
            '+' -> tokenize(PLUS)
            '*' -> tokenize(STAR)
            '^' -> tokenize(CARET)
            '%' -> tokenize(PERCENT)
            '-' -> tokenize(if (match('>')) ARROW else MINUS)
            '!' -> tokenize(if (match('=')) BANG_EQUAL else BANG)
            '=' -> tokenize(if (match('=')) EQUAL_EQUAL else EQUAL)
            '<' -> tokenize(if (match('=')) LESS_EQUAL else LESS)
            '>' -> tokenize(if (match('=')) GREATER_EQUAL else GREATER)
            '.' -> tokenize(if (match('.')) if (match('.')) TRIPLE_DOT else DOUBLE_DOT else DOT)
            ':' -> colonScanner()
            '?' -> questionScanner()
            '/' -> slashScanner()
            '"' -> doubleQuoteScanner()

            '&' -> tokenize(
                if (match('&')) AMP_AMP else {
                    handleError(ctx.line, "Unexpected '&' did you mean '&&'?"); continue
                }
            )

            '|' -> tokenize(
                if (match('|')) PIPE_PIPE else {
                    handleError(ctx.line, "Unexpected '|' did you mean '||'?"); continue
                }
            )

            else -> when {
                c.isDigit() -> digitScanner()
                c.isAlpha() -> identifierScanner()
                else -> handleError(ctx.line, "Unexpected character '$c'")
            }
        }
    }

    tokens.add(Token(EOF, "", Literal.None, ctx.line, startColumn))
    return tokens
}
