package dev.wezik.toy.parser

import dev.wezik.toy.lexer.Token

sealed interface Expr {
    data class Literal(val value: Token.Literal) : Expr
    data class Grouping(val expr: Expr) : Expr
    data class Unary(val op: Token, val right: Expr) : Expr
    data class Binary(val left: Expr, val op: Token, val righ: Expr) : Expr
}
