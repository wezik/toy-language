package dev.wezik.toy.parser

import dev.wezik.toy.lexer.Token

sealed interface Expr {
    sealed interface Literal : Expr {
        data class BoolValue(val value: Boolean) : Literal
        data class IntValue(val value: Int) : Literal
        data class DoubleValue(val value: Double) : Literal
        data class StringValue(val value: String) : Literal
        object Null : Literal
    }

    data class Grouping(val expr: Expr) : Expr
    data class Unary(val op: Token, val right: Expr) : Expr
    data class Binary(val left: Expr, val op: Token, val right: Expr) : Expr
    data class Variable(val name: Token) : Expr
    data class Assign(val name: Token, val value: Expr) : Expr
    data class Logical(val left: Expr, val op: Token, val right: Expr) : Expr
    data class FunLiteral(val params: List<Param>, val returnType: TypeExpr?, val body: List<Stmt>) : Expr
    data class Call(val callee: Expr, val paren: Token, val args: List<Expr>) : Expr
    data class Elvis(val left: Expr, val op: Token, val right: Expr) : Expr
}

data class Param(val name: Token, val type: TypeExpr)
