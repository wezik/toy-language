package dev.wezik.toy.checker

import dev.wezik.toy.checker.Type.*
import dev.wezik.toy.lexer.Token
import dev.wezik.toy.lexer.Token.Type.*
import dev.wezik.toy.parser.Expr
import dev.wezik.toy.parser.Stmt
import dev.wezik.toy.parser.TypeExpr

class TypeError(val name: Token, override val message: String) : RuntimeException()

class TypeEnv(private val parent: TypeEnv? = null) {
    private val types = mutableMapOf<String, Type>()

    fun declare(name: String, type: Type) {
        types[name] = type
    }

    fun get(name: Token): Type {
        if (name.text in types) return types[name.text]!!
        val parent = parent ?: throw TypeError(name, "Undefined variable '${name.text}'.")
        return parent.get(name)
    }
}

private fun Type.isNumber() = this in listOf(Type.IntT, Type.DoubleT, Type.AnyT)

fun typeCheck(stmts: List<Stmt>, env: TypeEnv = TypeEnv()): List<TypeError> {
    val errors = mutableListOf<TypeError>()
    for (stmt in stmts) {
        try {
            check(stmt, env)
        } catch (e: TypeError) {
            errors += e
        }
    }
    return errors
}

private fun resolve(expr: TypeExpr): Type = when (expr) {
    is TypeExpr.Named -> when (expr.name.text) {
        "Int" -> IntT
        "Double" -> DoubleT
        "Bool" -> BoolT
        "String" -> StringT
        else -> throw TypeError(expr.name, "Unknown type ${expr.name.text}.")
    }

    is TypeExpr.Function -> FunctionT(
        expr.params.map(::resolve),
        // For now null later, we might need explicit Unit type for function without return values
        expr.returns?.let(::resolve) ?: Type.NullT
    )
}

private fun typeOf(expr: Expr, env: TypeEnv): Type = when (expr) {
    is Expr.Literal.IntValue -> Type.IntT
    is Expr.Literal.DoubleValue -> Type.DoubleT
    is Expr.Literal.BoolValue -> Type.BoolT
    is Expr.Literal.StringValue -> Type.StringT
    is Expr.Literal.Null -> Type.NullT
    is Expr.Variable -> env.get(expr.name)
    is Expr.Grouping -> typeOf(expr.expr, env)
    is Expr.Binary -> binaryType(expr, env)
    else -> Type.AnyT
}

private fun binaryType(expr: Expr.Binary, env: TypeEnv): Type {
    val left = typeOf(expr.left, env)
    val right = typeOf(expr.right, env)

    // Some simple numeric casting
    fun numeric(): Type = when {
        left == Type.IntT && right == Type.IntT -> Type.IntT
        left.isNumber() && right.isNumber() -> Type.DoubleT
        else -> throw TypeError(expr.op, "Operands must be numbers, got $left and $right.")
    }

    return when (expr.op.type) {
        MINUS, STAR, SLASH -> numeric()
        GREATER, GREATER_EQUAL, LESS, LESS_EQUAL -> {
            numeric(); Type.BoolT
        }

        EQUAL_EQUAL, BANG_EQUAL -> Type.BoolT
        PLUS -> when {
            left == Type.StringT && right == Type.StringT -> Type.StringT
            left == Type.StringT || right == Type.StringT ->
                throw TypeError(expr.op, "'+' cannot concat $left and $right.")

            else -> numeric()
        }

        else -> throw TypeError(expr.op, "Unknown binary operator.")
    }
}

private fun check(stmt: Stmt, env: TypeEnv) {
    when (stmt) {
        is Stmt.Expression -> typeOf(stmt.expr, env)
        is Stmt.VarDecl -> {
            val inferred = typeOf(stmt.initializer, env)
            val declared = stmt.typeAnnotation?.let(::resolve) ?: inferred
            if (!assignable(from = inferred, to = declared)) {
                throw TypeError(stmt.name, "Cannot assign $inferred to '${stmt.name.text}: $declared'.")
            }
            env.declare(stmt.name.text, declared)
        }

        is Stmt.Block -> {
            val child = TypeEnv(env); for (s in stmt.stmts) check(s, child)
        }

        is Stmt.If -> {
            typeOf(stmt.condition, env); check(stmt.then, env); stmt.or?.let { check(it, env) }
        }

        is Stmt.While -> {
            typeOf(stmt.condition, env); check(stmt.then, env)
        }

        is Stmt.Return -> {
            stmt.expr?.let { typeOf(it, env) }
        }
    }
}

private fun assignable(from: Type, to: Type): Boolean = from == to || from == Type.AnyT || to == Type.AnyT
