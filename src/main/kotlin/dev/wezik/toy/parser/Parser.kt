package dev.wezik.toy.parser

import dev.wezik.toy.lexer.Token
import dev.wezik.toy.lexer.Token.Type.*
import dev.wezik.toy.parser.Expr.*

data class ParseError(val token: Token?, override val message: String) : RuntimeException()

sealed interface ParseResult {
    data class Ok(val stmts: List<Stmt>) : ParseResult
    data class Error(val errors: List<ParseError>) : ParseResult
}

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

    fun synchronize() {
        advance() // discard the bad token
        while (!isAtEnd()) {
            when (peek()?.type) {
                // sync points
                IF, FOR, RETURN, WHILE -> return
                else -> advance() // discard until sync point occurs
            }
        }
    }

    fun expression(): Expr {
        val expr = elivs()
        if (match(EQUAL)) {
            val value = expression()
            if (expr is Variable) return Assign(expr.name, value)
            throw ParseError(previous(), "Invalid assignment target.")
        }

        return expr
    }

    fun elivs(): Expr {
        var expr = or()
        while (match(QUESTION_COLON)) {
            val op = previous()
            expr = Elivs(expr, op, or())
        }
        return expr
    }

    fun or(): Expr {
        var expr = and()
        while (match(PIPE_PIPE)) {
            val op = previous()
            expr = Logical(expr, op, and())
        }
        return expr
    }

    fun and(): Expr {
        var expr = equality()
        while (match(AMP_AMP)) {
            val op = previous()
            expr = Logical(expr, op, equality())
        }
        return expr
    }

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

        // - val | + val
        while (match(MINUS, PLUS)) {
            val op = previous()
            val right = factor()
            expr = Binary(expr, op, right)
        }

        return expr
    }

    fun factor(): Expr {
        var expr = unary()

        // / val | * val
        while (match(SLASH, STAR)) {
            val op = previous()
            val right = unary()
            expr = Binary(expr, op, right)
        }

        return expr
    }

    fun unary(): Expr {
        // ! val | - val
        while (match(BANG, MINUS)) {
            val op = previous()
            return Unary(op, unary())
        }

        return call()
    }

    fun call(): Expr {
        var expr = primary()
        while (match(L_PAREN)) {
            val args = mutableListOf<Expr>()
            if (peek()?.type != R_PAREN) {
                do {
                    args += expression()
                } while (match(COMMA))
            }
            if (!match(R_PAREN)) throw ParseError(peek(), "Expected ')' after arguments.")
            expr = Call(expr, previous(), args)
        }
        return expr
    }

    fun primary(): Expr {
        // false | true | null
        when {
            match(FALSE) -> return Literal.BoolValue(false)
            match(TRUE) -> return Literal.BoolValue(true)
            match(NULL) -> return Literal.Null
        }

        // 15 | 15.0 | "15"
        if (match(INT, DOUBLE, STRING)) {
            val previous = previous()
            return when (val lit = previous.literal) {
                is Token.Literal.IntValue -> Literal.IntValue(lit.value)
                is Token.Literal.DoubleValue -> Literal.DoubleValue(lit.value)
                is Token.Literal.StringValue -> Literal.StringValue(lit.value)
                else -> throw ParseError(peek(), "(Parser problem) Expected literal to be handled.")
            }
        }

        // name
        if (match(IDENTIFIER)) return Variable(previous())

        // () { body } | (name: Type, ...) -> Type { body }
        if (peek()?.type == L_PAREN && isFunLiteralAhead()) {
            advance() // (
            return funLiteral(parseParams())
        }

        // (...)
        if (match(L_PAREN)) {
            val expr = expression()
            if (!match(R_PAREN)) throw ParseError(peek(), "Expected ')' after expression.")
            return Grouping(expr)
        }

        throw ParseError(peek(), "Expected expression.")
    }

    fun declaration(): Stmt {
        if (peek()?.type == IDENTIFIER && peek(1)?.type in listOf(COLON_EQUAL, DOUBLE_COLON, COLON)) {
            return varDecl()
        }

        return statement()
    }

    // name := expr | name :: expr | name: Type = expr
    fun varDecl(): Stmt {
        val name = advance() // identifier
        return when (advance().type) {
            COLON_EQUAL -> Stmt.VarDecl(name, expression(), mutable = true)
            DOUBLE_COLON -> Stmt.VarDecl(name, expression(), mutable = false)
            else -> {
                val typeAnnotation = parseType()
                if (!match(EQUAL)) throw ParseError(peek(), "Expected '=' after type annotation.")
                Stmt.VarDecl(name, expression(), mutable = true, typeAnnotation = typeAnnotation)
            }
        }
    }

    // {}
    fun block(): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        while (peek()?.type != R_BRACE && !isAtEnd()) {
            stmts += declaration()
        }
        if (!match(R_BRACE)) throw ParseError(peek(), "Expected '}' after block.")
        return stmts
    }

    fun statement(): Stmt {
        return when {
            match(IF) -> ifStatement()
            match(WHILE) -> whileStatement()
            match(L_BRACE) -> Stmt.Block(block())
            match(RETURN) -> returnStatement()
            else -> Stmt.Expression(expression())
        }
    }

    fun ifStatement(): Stmt {
        val condition = expression()
        val then = statement()
        val or = if (match(ELSE)) statement() else null
        return Stmt.If(condition, then, or)
    }

    fun whileStatement(): Stmt {
        val condition = expression()
        val then = statement()
        return Stmt.While(condition, then)
    }

    fun returnStatement(): Stmt {
        val expr = if (peek()?.type != R_BRACE && !isAtEnd()) expression() else null
        return Stmt.Return(expr)
    }

    fun parseType(): TypeExpr {
        val base = parseBaseType()
        return if (match(QUESTION)) TypeExpr.Nullable(base) else base
    }

    fun parseBaseType(): TypeExpr {
        if (match(L_PAREN)) {
            val params = mutableListOf<TypeExpr>()
            if (peek()?.type != R_PAREN) {
                do {
                    params += parseType()
                } while (match(COMMA))
            }
            if (!match(R_PAREN)) throw ParseError(peek(), "Expected ')' in function type.")
            if (!match(ARROW)) throw ParseError(peek(), "Expected '->' in function type.")
            return TypeExpr.Function(params, parseType())
        }
        if (peek()?.type == IDENTIFIER) return TypeExpr.Named(advance())
        throw ParseError(peek(), "Expected a type.")
    }

    // current token is '(', looks past it for '() {' / '() ->' / '(name: Type'
    fun isFunLiteralAhead(): Boolean {
        if (peek(1)?.type == R_PAREN) return peek(2)?.type == ARROW || peek(2)?.type == L_BRACE
        return peek(1)?.type == IDENTIFIER && peek(2)?.type == COLON
    }

    fun parseParams(): List<Param> {
        val result = mutableListOf<Param>()
        if (peek()?.type != R_PAREN) {
            do {
                val name = advance()
                if (!match(COLON)) throw ParseError(peek(), "Expected ':' after parameter name.")
                result += Param(name, parseType())
            } while (match(COMMA))
        }
        if (!match(R_PAREN)) throw ParseError(peek(), "Expected ')' after parameters.")
        return result
    }

    fun funLiteral(params: List<Param>): Expr {
        val returnType = if (match(ARROW)) parseType() else null
        if (!match(L_BRACE)) throw ParseError(peek(), "Expected '{' for function body.")
        val body = block()
        return Expr.FunLiteral(params, returnType, body)
    }

}

fun parse(tokens: List<Token>): ParseResult {
    val stmts = mutableListOf<Stmt>()
    val errors = mutableListOf<ParseError>()
    val ctx = TokenContext(tokens)

    while (!ctx.isAtEnd()) {
        try {
            stmts += ctx.declaration()
        } catch (e: ParseError) {
            errors += e
            ctx.synchronize()
        }
    }

    return if (errors.isEmpty()) ParseResult.Ok(stmts) else ParseResult.Error(errors)
}
