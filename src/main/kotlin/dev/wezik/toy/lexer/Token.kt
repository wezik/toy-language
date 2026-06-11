package dev.wezik.toy.lexer

/**
 * Token produced by the lexer
 */
data class Token(
    /** Enumerated type of the token **/
    val type: Type,
    /** Text representation - literal, how the token is written **/
    val text: String,
    /** Literal value of the token, integer, double, string without quotes... **/
    val literal: Literal,
    /** Line on which the token occurred **/
    val line: Int,
    /** Column on which the token occurred **/
    val column: Int,
) {
    enum class Type {
        // LITERALS
        IDENTIFIER, STRING, INT, DOUBLE,

        // ASSIGNMENTS
        COLON, DOUBLE_COLON, EQUAL, COLON_EQUAL,

        // SUGAR
        ARROW, COMMA, DOT, DOUBLE_DOT, TRIPLE_DOT, QUESTION, QUESTION_COLON, QUESTION_DOT,

        // OPERATORS
        CARET, MINUS, PERCENT, PLUS, STAR, SLASH,

        // COMPARATORS
        BANG, BANG_EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,

        // GROUPING
        L_PAREN, R_PAREN, L_BRACE, R_BRACE, L_BRACKET, R_BRACKET,

        // RESERVED WORDS
        ELSE, ENUM, FALSE, FOR, IF, NULL, RETURN, STRUCT, TRUE,

        // TODO: remove once native function calls are supported
        PRINT,

        EOF,
    }

    sealed class Literal {
        data class IntValue(val value: Int) : Literal()
        data class DoubleValue(val value: Double) : Literal()
        data class StringValue(val value: String) : Literal()
        object None : Literal()

        override fun toString(): String {
            val type = when (this) {
                is IntValue -> "Int($value)"
                is DoubleValue -> "Double($value)"
                is StringValue -> "String($value)"
                is None -> "None"
            }
            return "Literal($type)"
        }
    }

    override fun toString(): String {
        return "Token($type, '$text', $literal, $line:$column)"
    }
}
