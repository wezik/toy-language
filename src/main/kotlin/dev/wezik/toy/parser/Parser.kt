package dev.wezik.toy.parser

import dev.wezik.toy.lexer.Token
import dev.wezik.toy.lexer.Token.Type.*
import dev.wezik.toy.parser.Expr.*

data class ParseError(val token: Token?, override val message: String) : RuntimeException()

private class TokenContext(val tokens: List<Token>) {
    var current = 0

    fun peek(offset: Int = 0) = tokens.getOrNull(current + offset)
    fun previous() = peek(-1) ?: throw ParseError(peek(), "(Parser problem) expected consumed token to still exist")
    fun isAtEnd() = peek()?.type == EOF

    fun advance(): Token {
        val token = tokens[current]
        if (!isAtEnd()) current++
        return token
    }

    fun match(vararg types: Token.Type): Boolean {
        if (isAtEnd()) return false

        for (type in types) {
            val peeked = peek() ?: continue
            if (peeked.type == type) {
                advance()
                return true
            }
        }

        return false
    }

    // parser handling

    fun expression() = equality()

    fun equality(): Expr {
        var expr = comparison()

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            val op = previous()
            val right = comparison()
            expr = Binary(expr, op, right)
        }

        return expr
    }

    fun comparison(): Expr {
        var expr = term()

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            val op = previous()
            val right = term()
            expr = Binary(expr, op, right)
        }

        return expr
    }

    fun term(): Expr {
        var expr = factor()

        while (match(MINUS, PLUS)) {
            val op = previous()
            val right = factor()
            expr = Binary(expr, op, right)
        }

        return expr
    }

    fun factor(): Expr {
        var expr = unary()

        while (match(SLASH, STAR)) {
            val op = previous()
            val right = unary()
            expr = Binary(expr, op, right)
        }

        return expr
    }

    fun unary(): Expr {
        while (match(BANG, MINUS)) {
            val op = previous()
            val right = unary()
            return Unary(op, right)
        }

        return primary()
    }

    fun primary(): Expr {
        when {
            match(FALSE) -> return Literal.BoolValue(false)
            match(TRUE) -> return Literal.BoolValue(true)
            match(NULL) -> return Literal.Null
        }

        if (match(INT, DOUBLE, STRING)) {
            val previous = previous()
            return when (val lit = previous.literal) {
                is Token.Literal.IntValue -> Literal.IntValue(lit.value)
                is Token.Literal.DoubleValue -> Literal.DoubleValue(lit.value)
                is Token.Literal.StringValue -> Literal.StringValue(lit.value)
                else -> throw ParseError(peek(), "(Parser problem) Expected literal to be handled.")
            }
        }

        if (match(L_PAREN)) {
            val expr = expression()
            if (!match(R_PAREN)) throw ParseError(peek(), "Expected ')' after expression.")
            return Grouping(expr)
        }

        throw ParseError(peek(), "Expected expression.")
    }
}

sealed interface ParseResult {
    data class Ok(val expr: Expr) : ParseResult
    data class Error(val errors: List<ParseError>) : ParseResult
}

fun parse(tokens: List<Token>): ParseResult {
    var expr: Expr? = null
    val errors = mutableListOf<ParseError>()

    try {
        expr = TokenContext(tokens).expression()
    } catch (e: ParseError) {
        errors += e
    }
    return if (expr != null) ParseResult.Ok(expr) else ParseResult.Error(errors)
}
