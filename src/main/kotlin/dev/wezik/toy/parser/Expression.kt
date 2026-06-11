package dev.wezik.toy.parser

import dev.wezik.toy.lexer.Token

sealed interface Expr {
    sealed interface Literal : Expr {
        data class BoolValue(val value: Boolean) : Literal {
            override fun toString() = "$value"
        }

        data class IntValue(val value: Int) : Literal {
            override fun toString() = "$value"
        }

        data class DoubleValue(val value: Double) : Literal {
            override fun toString() = "$value"
        }

        data class StringValue(val value: String) : Literal {
            override fun toString() = "\"$value\""
        }

        object Null : Literal {
            override fun toString() = "null"
        }
    }

    data class Grouping(val expr: Expr) : Expr {
        override fun toString() = "($expr)"
    }

    data class Unary(val op: Token, val right: Expr) : Expr {
        override fun toString() = "${op.text} $right"
    }

    data class Binary(val left: Expr, val op: Token, val right: Expr) : Expr {
        override fun toString() = "(${op.text} $left $right)"
    }

    data class Variable(val name: Token) : Expr {
        override fun toString() = name.text
    }
}
