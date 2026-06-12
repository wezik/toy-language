package dev.wezik.toy.checker

sealed interface Type {
    object IntT : Type
    object DoubleT : Type
    object BoolT : Type
    object StringT : Type
    object NullT : Type       // type of the null literal itself
    object AnyT : Type        // TODO: temp escape hatch
    data class Optional(val inner: Type) : Type   // type of a T? slot
    data class FunctionT(val params: List<Type>, val returns: Type) : Type

    fun isNumber() = this == IntT || this == DoubleT || this == AnyT
}
