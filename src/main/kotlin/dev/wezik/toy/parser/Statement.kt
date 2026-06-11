package dev.wezik.toy.parser

import dev.wezik.toy.lexer.Token

sealed interface Stmt {
    data class Expression(val expr: Expr) : Stmt

    data class VarDecl(
        val name: Token,
        val intializer: Expr,
        val mutable: Boolean,
    ) : Stmt

    // TODO: remove once native function calls are supported
    data class Print(val expr: Expr) : Stmt
}
