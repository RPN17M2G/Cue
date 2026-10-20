package io.github.rpn17m2g.cue.data.metadata

/** Typed lookup failures. Providers never throw these across their public boundary. */
sealed interface LookupError {
    data class Network(val cause: Throwable? = null) : LookupError
    data class Http(val code: Int) : LookupError
    data object RateLimited : LookupError
    data object MissingKey : LookupError
    data class Parse(val cause: Throwable? = null) : LookupError
}

/** Success or a typed [LookupError]; callers never need to catch provider exceptions. */
sealed interface LookupResult<out T> {
    data class Success<T>(val value: T) : LookupResult<T> {
        val data: T get() = value
    }

    data class Failure(val error: LookupError) : LookupResult<Nothing>
}

internal fun httpFailure(code: Int): LookupResult.Failure =
    if (code == 429) {
        LookupResult.Failure(LookupError.RateLimited)
    } else {
        LookupResult.Failure(LookupError.Http(code))
    }
