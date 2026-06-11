package dev.wezik.toy.interpreter

import dev.wezik.toy.lexer.Token

class Environment(private val parent: Environment? = null) {

    private data class Binding(val value: Any?, val mutable: Boolean)

    private val bindings = mutableMapOf<String, Binding>()

    fun get(name: Token): Any? {
        if (name.text in bindings) return bindings[name.text]!!.value
        return parent?.get(name) ?: throw EvalError(name, "Undefined variable '${name.text}'.")
    }

    fun declare(name: Token, value: Any?, mutable: Boolean) {
        bindings[name.text] = Binding(value, mutable)
    }

    fun assign(name: Token, value: Any?) {
        if (name.text in bindings) {
            if (!bindings[name.text]!!.mutable) {
                throw EvalError(name, "Cannot assign to immutable '${name.text}'.")
            }

            bindings[name.text] = Binding(value, mutable = true)
            return
        }
        parent?.assign(name, value) ?: throw EvalError(name, "Undefined variable '${name.text}'.")
    }
}
