package dev.wezik.toy.interpreter

import dev.wezik.toy.lexer.Token
import dev.wezik.toy.lexer.Token.Type.*
import dev.wezik.toy.parser.Expr
import dev.wezik.toy.parser.Expr.*

data class EvalError(val token: Token?, override val message: String) : RuntimeException()

sealed interface EvalResult {
    data class Ok(val value: Any?) : EvalResult
    data class Error(val errors: List<EvalError>) : EvalResult
}

fun interpret(expr: Expr): EvalResult {
    val errors = mutableListOf<EvalError>()
    // For now Any? as I am not sure how to utilize the type system in here
    var value: Any? = null
    try {
        value = eval(expr)
    } catch (e: EvalError) {
        errors += e
    }
    return if (errors.isEmpty()) EvalResult.Ok(value) else EvalResult.Error(errors)
}

private fun eval(expr: Expr): Any? = when (expr) {
    is Literal.BoolValue -> expr.value
    is Literal.IntValue -> expr.value
    is Literal.DoubleValue -> expr.value
    is Literal.StringValue -> expr.value
    is Literal.Null -> null
    is Grouping -> eval(expr.expr)
    is Unary -> unary(expr)
    is Binary -> binary(expr)
}

private fun Any?.isTruthy() = this != null && this != false

private fun unary(expr: Unary): Any {
    val right = eval(expr.right)
    return when (expr.op.type) {
        BANG -> !right.isTruthy()
        MINUS -> when (right) {
            is Int -> -right
            is Double -> -right
            else -> throw EvalError(expr.op, "Operrand must be a number.")
        }

        else -> throw EvalError(expr.op, "Unknown unary operator.")
    }
}

private fun binary(expr: Binary): Any {
    val left = eval(expr.left)
    val right = eval(expr.right)

    fun numericOp(intOp: (Int, Int) -> Any, doubleOp: (Double, Double) -> Any): Any = when {
        left is Int && right is Int -> intOp(left, right)
        left is Number && right is Number -> doubleOp(left.toDouble(), right.toDouble())
        else -> throw EvalError(expr.op, "Operands must be numbers.")
    }

    fun Int.divWithGuards(other: Int): Int {
        if (other == 0) throw EvalError(expr.op, "Divison by zero.")
        return this / other
    }

    return when (expr.op.type) {
        MINUS -> numericOp(Int::minus, Double::minus)
        STAR -> numericOp(Int::times, Double::times)
        SLASH -> numericOp(Int::divWithGuards, Double::div)
        GREATER -> numericOp({ a, b -> a > b }, { a, b -> a > b })
        GREATER_EQUAL -> numericOp({ a, b -> a >= b }, { a, b -> a >= b })
        LESS -> numericOp({ a, b -> a < b }, { a, b -> a < b })
        LESS_EQUAL -> numericOp({ a, b -> a <= b }, { a, b -> a <= b })
        EQUAL_EQUAL -> left == right
        BANG_EQUAL -> left != right
        PLUS -> when {
            left is String && right is String -> left + right
            else -> numericOp(Int::plus, Double::plus)
        }

        else -> throw EvalError(expr.op, "Unknown binary operator.")
    }
}
