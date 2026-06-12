package dev.wezik.toy.parser

import dev.wezik.toy.lexer.Token

sealed interface TypeExpr {
    data class Named(val name: Token) : TypeExpr
    data class Nullable(val inner: TypeExpr) : TypeExpr
    data class Function(val params: List<TypeExpr>, val returns: TypeExpr?) : TypeExpr
}
