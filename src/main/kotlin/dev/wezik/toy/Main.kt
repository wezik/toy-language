package dev.wezik.toy

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

fun main(args: Array<String>) {
    if (args.isEmpty()) runInteractive() else runFile(args[0])
}

fun runInteractive() {
    val input = InputStreamReader(System.`in`)
    val reader = BufferedReader(input)

    fun BufferedReader.readWithPrompt(): String? {
        print("> ")
        return readLine()
    }

    while (true) {
        val line = reader.readWithPrompt() ?: break
        run(line)
    }
}

fun runFile(filePath: String) {
    val bytes = File(filePath).readBytes()
    run(String(bytes))
    // if (scannerContext.hadError) exitProcess(65)
}

fun run(source: String) {
    val tokens = scan(source)
    println("Tokens: ${tokens.joinToString(" ")}")

    // val expr = parse(tokens)
    // println("Expr: $expr")

    // val result = interpret(expr)
    // println("Result: $result")
}

// PARSER
// sealed interface Expr {
//     data class Literal(val value: Any?) : Expr
//     data class Grouping(val expr: Expr) : Expr
//     data class Unary(val operator: Token, val right: Expr) : Expr
//     data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr
// }

// fun parse(tokens: List<Token>): Expr {
//     var current = 0

//     fun peek(offset: Int = 0) = tokens[current + offset]
//     fun isAtEnd() = peek().type == TokenType.EOF
//     fun previous() = tokens[current - 1]
//     fun check(type: TokenType) = if (isAtEnd()) false else peek().type == type

//     fun next(): Token {
//         if (!isAtEnd()) current++
//         return previous()
//     }

//     fun match(vararg types: TokenType): Boolean {
//         for (type in types) {
//             if (check(type)) {
//                 next()
//                 return true
//             }
//         }

//         return false
//     }

//     // I know... I just don't want to make it objective and scatter all around the place
//     // it won't be null at the time of execution anyway
//     var equality: (() -> Expr)? = null

//     fun expression(): Expr {
//         return equality!!.invoke()
//     }

//     fun consume(type: TokenType, message: String) {
//         if (!check(type)) error(peek().toString() + message)
//         next()
//     }

//     fun primary(): Expr {
//         if (match(TokenType.FALSE)) return Expr.Literal(false)
//         if (match(TokenType.TRUE)) return Expr.Literal(true)
//         if (match(TokenType.NULL)) return Expr.Literal(null)

//         if (match(TokenType.INT_NUMBER, TokenType.FLOAT_NUMBER, TokenType.STRING)) {
//             return Expr.Literal(previous().literal)
//         }

//         if (match(TokenType.L_PAREN)) {
//             val expr = expression()
//             consume(TokenType.R_PAREN, "Expected ')' after expression.")
//             return Expr.Grouping(expr)
//         }

//         error("failed to match primary token and fallen through all the cases")
//     }

//     fun unary(): Expr {
//         if (match(TokenType.BANG, TokenType.MINUS)) {
//             val op = previous()
//             val right = unary()
//             return Expr.Unary(op, right)
//         }

//         return primary()
//     }

//     fun factor(): Expr {
//         var expr = unary()

//         while (match(TokenType.SLASH, TokenType.STAR)) {
//             val op = previous()
//             val right = unary()
//             expr = Expr.Binary(expr, op, right)
//         }

//         return expr
//     }

//     fun term(): Expr {
//         var expr = factor()

//         while (match(TokenType.MINUS, TokenType.PLUS)) {
//             val op = previous()
//             val right = factor()
//             expr = Expr.Binary(expr, op, right)
//         }

//         return expr
//     }

//     fun comparison(): Expr {
//         var expr = term()

//         while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
//             val op = previous()
//             val right = term()
//             expr = Expr.Binary(expr, op, right)
//         }

//         return expr
//     }

//     equality = {
//         var expr = comparison()

//         while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
//             val op = previous()
//             val right = comparison()
//             expr = Expr.Binary(expr, op, right)
//         }

//         expr
//     }

//     return expression()
// }

// // INTERPRETER
// sealed interface Frame
// class Eval(val expr: Expr) : Frame
// data class ApplyUnary(val op: Token) : Frame
// data class ApplyBinaryOp(val op: Token, val right: Expr) : Frame
// data class FinishBinary(val op: Token, val left: Any?) : Frame

// fun interpret(expr: Expr): Any? {

//     val work = ArrayDeque<Frame>()
//     val values = ArrayDeque<Any?>()

//     fun isTruthy(value: Any?) = value != null && value != false

//     fun applyUnary(op: Token, right: Any?): Any = when (op.type) {
//         TokenType.BANG -> !isTruthy(right)
//         TokenType.MINUS -> when (right) {
//             is Int -> -right
//             is Double -> -right
//             else -> error("[line ${op.line}] Operand must be a number.")
//         }
//         else -> error("[line ${op.line}] Unknown unary operator.")
//     }

//     fun applyBinary(op: Token, left: Any?, right: Any?): Any {
//         fun numericOp(intOp: (Int, Int) -> Any, doubleOp: (Double, Double) -> Any): Any =
//             when (left) {
//                 is Int if right is Int -> intOp(left, right)
//                 is Number if right is Number -> doubleOp(left.toDouble(), right.toDouble())
//                 else -> error("[line ${op.line}] Operands must be numbers.")
//             }

//         return when (op.type) {
//             TokenType.PLUS -> when {
//                 left is String && right is String -> left + right
//                 else -> numericOp(Int::plus, Double::plus)
//             }
//             TokenType.MINUS -> numericOp(Int::minus, Double::minus)
//             TokenType.STAR -> numericOp(Int::times, Double::times)
//             TokenType.SLASH -> numericOp(
//                 { a, b -> if (b == 0) error("[line ${op.line}] Division by zero.") else a / b },
//                 Double::div
//             )
//             TokenType.GREATER -> numericOp({ a, b -> a > b }, { a, b -> a > b })
//             TokenType.GREATER_EQUAL -> numericOp({ a, b -> a >= b }, { a, b -> a >= b })
//             TokenType.LESS -> numericOp({ a, b -> a < b }, { a, b -> a < b })
//             TokenType.LESS_EQUAL -> numericOp({ a, b -> a <= b }, { a, b -> a <= b })
//             TokenType.EQUAL_EQUAL -> left == right
//             TokenType.BANG_EQUAL -> left != right
//             else -> error("[line ${op.line}] Unknown binary operator.")
//         }
//     }

//     work.addLast(Eval(expr))

//     while (work.isNotEmpty()) {
//         when (val frame = work.removeLast()) {
//             is Eval -> when (val e = frame.expr) {
//                 is Expr.Literal -> values.addLast(e.value)
//                 is Expr.Grouping -> work.addLast(Eval(e.expr))
//                 is Expr.Unary -> {
//                     work.addLast(ApplyUnary(e.operator))
//                     work.addLast(Eval(e.right))
//                 }
//                 is Expr.Binary -> {
//                     work.addLast(ApplyBinaryOp(e.operator, e.right))
//                     work.addLast(Eval(e.left))
//                 }
//             }
//             is ApplyUnary -> values.addLast(applyUnary(frame.op, values.removeLast()))
//             is ApplyBinaryOp -> {
//                 val left = values.removeLast()
//                 work.addLast(FinishBinary(frame.op, left))
//                 work.addLast(Eval(frame.right))
//             }
//             is FinishBinary -> values.addLast(applyBinary(frame.op, frame.left, values.removeLast()))
//         }
//     }

//     return values.last()
// }
