package dev.wezik.toy.interpreter

import dev.wezik.toy.lexer.Token

class Environment(private val parent: Environment? = null) {

    private data class Binding(val value: Any?, val mutable: Boolean)

    private val bindings = mutableMapOf<String, Binding>()

    fun get(name: Token): Any? {
        if (name.text in bindings) return bindings[name.text]!!.value
        return parent?.get(name) ?: throw EvalError(name, "Undefined variable '${name.text}'.")
    }

    fun declare(name: String, value: Any?, mutable: Boolean) {
        // simplified declaration for now, no mutability or assignments
        bindings[name] = Binding(value, mutable)
    }
}
