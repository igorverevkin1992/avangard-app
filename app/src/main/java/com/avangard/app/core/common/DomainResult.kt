package com.avangard.app.core.common

sealed interface DomainResult<out T, out E> {
    data class Ok<T>(val value: T) : DomainResult<T, Nothing>
    data class Err<E>(val error: E) : DomainResult<Nothing, E>
}

inline fun <T, E, R> DomainResult<T, E>.map(transform: (T) -> R): DomainResult<R, E> = when (this) {
    is DomainResult.Ok -> DomainResult.Ok(transform(value))
    is DomainResult.Err -> this
}
