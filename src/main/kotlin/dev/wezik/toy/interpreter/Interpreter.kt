package dev.wezik.toy.interpreter

import dev.wezik.toy.lexer.Token
import dev.wezik.toy.lexer.Token.Type.*
import dev.wezik.toy.parser.Expr
import dev.wezik.toy.parser.Expr.*
import dev.wezik.toy.parser.Stmt
import dev.wezik.toy.parser.Stmt.*

data class EvalError(val token: Token?, override val message: String) : RuntimeException()

sealed interface InterpretResult {
    object Ok : InterpretResult
    data class Error(val errors: List<EvalError>) : InterpretResult
}

fun interpret(stmts: List<Stmt>, env: Environment = Environment()): InterpretResult {
    val errors = mutableListOf<EvalError>()
    for (stmt in stmts) {
        try {
            exec(stmt, env)
        } catch (e: EvalError) {
            errors += e
        }
    }
    return if (errors.isEmpty()) InterpretResult.Ok else InterpretResult.Error(errors)
}

private fun exec(stmt: Stmt, env: Environment) {
    when (stmt) {
        is Expression -> eval(stmt.expr, env)
        is VarDecl -> env.declare(stmt.name, eval(stmt.intializer, env), stmt.mutable)
        is Block -> {
            val child = Environment(env)
            for (s in stmt.stmts) exec(s, child)
        }

        is If -> {
            if (eval(stmt.condition, env).isTruthy()) exec(stmt.then, env)
            else stmt.or?.let { exec(it, env) }
        }

        // TODO: remove once native function calls are supported
        is Print -> println(eval(stmt.expr, env) ?: "null")
    }
}

private fun eval(expr: Expr, env: Environment): Any? = when (expr) {
    is Literal.BoolValue -> expr.value
    is Literal.IntValue -> expr.value
    is Literal.DoubleValue -> expr.value
    is Literal.StringValue -> expr.value
    is Literal.Null -> null
    is Variable -> env.get(expr.name)
    is Grouping -> eval(expr.expr, env)
    is Unary -> unary(expr, env)
    is Binary -> binary(expr, env)
    is Assign -> {
        val value = eval(expr.value, env)
        env.assign(expr.name, value)
        value
    }
}

private fun Any?.isTruthy() = this != null && this != false

private fun unary(expr: Unary, env: Environment): Any {
    val right = eval(expr.right, env)
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

private fun binary(expr: Binary, env: Environment): Any {
    val left = eval(expr.left, env)
    val right = eval(expr.right, env)

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
            left is String || right is String -> throw EvalError(
                expr.op,
                "${expr.op.text} cannot mix String with other types."
            )

            else -> numericOp(Int::plus, Double::plus)
        }

        else -> throw EvalError(expr.op, "Unknown binary operator.")
    }
}
