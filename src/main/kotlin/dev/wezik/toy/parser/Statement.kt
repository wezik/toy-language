package dev.wezik.toy.parser

import dev.wezik.toy.lexer.Token
import dev.wezik.toy.parser.TypeExpr

sealed interface Stmt {
    data class Expression(val expr: Expr) : Stmt

    data class VarDecl(
        val name: Token,
        val intializer: Expr,
        val mutable: Boolean,
        val typeAnnotation: TypeExpr? = null,
    ) : Stmt

    data class Block(val stmts: List<Stmt>) : Stmt
    data class If(val condition: Expr, val then: Stmt, val or /* else */: Stmt?) : Stmt
    data class While(val condition: Expr, val then: Stmt) : Stmt
    data class Return(val expr: Expr?) : Stmt

    // TODO: remove once native function calls are supported
    data class Print(val expr: Expr) : Stmt
}
