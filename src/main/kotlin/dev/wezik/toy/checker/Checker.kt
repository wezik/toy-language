package dev.wezik.toy.checker

import dev.wezik.toy.checker.Type.*
import dev.wezik.toy.lexer.Token
import dev.wezik.toy.lexer.Token.Type.*
import dev.wezik.toy.parser.Expr
import dev.wezik.toy.parser.Stmt
import dev.wezik.toy.parser.TypeExpr

class TypeError(val name: Token?, override val message: String) : RuntimeException()

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

    is TypeExpr.Optional -> Type.Optional(resolve(expr.inner))
}

private fun typeOf(expr: Expr, env: TypeEnv): Type = when (expr) {
    is Expr.Literal.IntValue -> IntT
    is Expr.Literal.DoubleValue -> DoubleT
    is Expr.Literal.BoolValue -> BoolT
    is Expr.Literal.StringValue -> StringT
    is Expr.Literal.Null -> NullT
    is Expr.Variable -> env.get(expr.name)
    is Expr.Grouping -> typeOf(expr.expr, env)
    is Expr.Binary -> binaryType(expr, env)
    is Expr.Unary -> unaryType(expr, env)
    is Expr.Logical -> logicalType(expr, env)
    is Expr.Assign -> assignType(expr, env)
    is Expr.FunLiteral -> funLiteralType(expr, env)
    is Expr.Call -> callType(expr, env)
    is Expr.Elvis -> elvisType(expr, env)
}

private fun elvisType(expr: Expr.Elvis, env: TypeEnv): Type {
    val leftType = typeOf(expr.left, env)
    val rightType = typeOf(expr.right, env)
    if (leftType !is Type.Optional && leftType != AnyT) {
        throw TypeError(expr.op, "Left side of '?:' is not optional (got $leftType).")
    }

    return if (leftType is Type.Optional) leftType.inner else rightType
}

private fun callType(expr: Expr.Call, env: TypeEnv): Type {
    val calleeType = typeOf(expr.callee, env)
    if (calleeType !is FunctionT && calleeType != AnyT) {
        throw TypeError(expr.paren, "Value of type $calleeType is not callable.")
    }
    if (calleeType == AnyT) return AnyT // TODO: temp escape hatch

    val fn = calleeType as FunctionT
    if (expr.args.size != fn.params.size) {
        throw TypeError(expr.paren, "Expected ${fn.params.size} args, got ${expr.args.size}.")
    }

    expr.args.zip(fn.params).forEach { (arg, expected) ->
        val actual = typeOf(arg, env)
        if (!assignable(from = actual, to = expected)) {
            throw TypeError(tokenOf(arg), "Argument type mismatch: expected $expected, got $actual.")
        }
    }
    return fn.returns
}

private fun funLiteralType(expr: Expr.FunLiteral, env: TypeEnv): Type {
    val paramTypes = expr.params.map { resolve(it.type) }
    val returnType = expr.returnType?.let(::resolve) ?: NullT
    val fnEnv = TypeEnv(env)
    expr.params.zip(paramTypes).forEach { (param, type) ->
        fnEnv.declare(param.name.text, type)
    }

    for (s in expr.body) check(s, fnEnv, returnType)

    return FunctionT(paramTypes, returnType)
}

private fun assignType(expr: Expr.Assign, env: TypeEnv): Type {
    val declared = env.get(expr.name)
    val incoming = typeOf(expr.value, env)
    if (!assignable(from = incoming, to = declared)) {
        throw TypeError(expr.name, "Cannot assign $incoming to '${expr.name.text}: $declared'.")

    }
    return declared
}

private fun logicalType(expr: Expr.Logical, env: TypeEnv): Type {
    typeOf(expr.left, env)
    typeOf(expr.right, env)
    return BoolT
}

private fun unaryType(expr: Expr.Unary, env: TypeEnv): Type {
    return when (expr.op.type) {
        BANG -> {
            typeOf(expr.right, env)
            BoolT
        }

        MINUS -> {
            val type = typeOf(expr.right, env)
            if (!type.isNumber()) throw TypeError(expr.op, "'-' requires a number, got $type.")
            type
        }

        else -> throw TypeError(expr.op, "Unknown unary operator.")
    }
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

private fun check(stmt: Stmt, env: TypeEnv, returnType: Type? = null) {
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
            val child = TypeEnv(env)
            for (s in stmt.stmts) check(s, child, returnType)
        }

        is Stmt.If -> {
            val narrowedEnv = narrow(stmt.condition, env)
            typeOf(stmt.condition, env)
            check(stmt.then, narrowedEnv, returnType)
            stmt.or?.let { check(it, env, returnType) }
        }

        is Stmt.While -> {
            typeOf(stmt.condition, env)
            check(stmt.then, env, returnType)
        }

        is Stmt.Return -> {
            val actual = stmt.expr?.let { typeOf(it, env) } ?: NullT
            val expected = returnType ?: NullT
            if (!assignable(from = actual, to = expected)) {
                throw TypeError(
                    stmt.expr?.let { tokenOf(it) },
                    "Return type mismatch: expected $expected got $actual."
                )
            }
        }
    }
}

private fun assignable(from: Type, to: Type): Boolean = when {
    from == to -> true
    from == AnyT || to == AnyT -> true
    to is Type.Optional && from == NullT -> true
    to is Type.Optional -> assignable(from, to.inner)
    else -> false
}

private fun tokenOf(expr: Expr): Token? = when (expr) {
    is Expr.Variable -> expr.name
    is Expr.Assign -> expr.name
    is Expr.Call -> expr.paren
    is Expr.Binary -> expr.op
    is Expr.Unary -> expr.op
    is Expr.Logical -> expr.op
    is Expr.Elvis -> expr.op
    else -> null
}

private fun narrow(condition: Expr, env: TypeEnv): TypeEnv {
    // for now only support `x != null` narrowing
    // TODO: figure out some good meta-programming pattern for "assuming" types
    if (condition is Expr.Binary && condition.op.type == BANG_EQUAL) {
        val left = condition.left
        val right = condition.right
        if (right is Expr.Literal.Null && left is Expr.Variable) {
            val declared = runCatching { env.get(left.name) }.getOrNull()
            if (declared is Type.Optional) {
                val child = TypeEnv(env)
                child.declare(left.name.text, declared.inner) // unwrap
                return child
            }
        }
        // also the reverse
        if (left is Expr.Literal.Null && right is Expr.Variable) {
            val declared = runCatching { env.get(right.name) }.getOrNull()
            if (declared is Type.Optional) {
                val child = TypeEnv(env)
                child.declare(right.name.text, declared.inner) // unwrap
                return child
            }
        }
    }

    return env
}
