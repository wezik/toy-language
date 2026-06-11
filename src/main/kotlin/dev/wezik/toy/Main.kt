package dev.wezik.toy

import dev.wezik.toy.lexer.Token
import dev.wezik.toy.lexer.scan
import dev.wezik.toy.parser.ParseResult
import dev.wezik.toy.parser.parse
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

    val result = parse(tokens)
    if (result is ParseResult.Error) {
        for (e in result.errors) {
            if (e.token?.type == Token.Type.EOF) {
                System.err.println("[line ${e.token.line}] at the end: ${e.message}")
            } else {
                System.err.println("[line ${e.token?.line}:${e.token?.column}] at ${e.token?.text}: ${e.message}")
            }
        }
        return
    }

    // force the type
    if (result !is ParseResult.Ok) error("???")
    println("Expr: ${result.expr}")

    // val result = interpret(expr)
    // println("Result: $result")
}

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
