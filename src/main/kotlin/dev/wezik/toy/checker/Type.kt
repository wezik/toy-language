package dev.wezik.toy.checker

sealed interface Type {
    object IntT : Type
    object DoubleT : Type
    object BoolT : Type
    object StringT : Type
    object NullT : Type

    // TODO: temp escape hatch
    object AnyT : Type

    data class FunctionT(val params: List<Type>, val returns: Type) : Type
}
