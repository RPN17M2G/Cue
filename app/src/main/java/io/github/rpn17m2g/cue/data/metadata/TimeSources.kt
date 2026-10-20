package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.settings.AppSettings

fun interface WallClock {
    fun nowMillis(): Long

    companion object {
        val System = WallClock { java.lang.System.currentTimeMillis() }
    }
}

fun interface DelayProvider {
    suspend fun delay(millis: Long)

    companion object {
        val Default = DelayProvider { millis -> kotlinx.coroutines.delay(millis) }
        val None = DelayProvider { }
    }
}

/** Blocking sleep used only by the OkHttp retry interceptor. Tests inject a recorder. */
fun interface Sleeper {
    fun sleep(millis: Long)

    companion object {
        val System = Sleeper { millis -> if (millis > 0L) Thread.sleep(millis) }
        val None = Sleeper { }
    }
}

data class NetworkConfig(
    val timeoutMs: Long = AppSettings.DEFAULT_HTTP_TIMEOUT_MS,
    val retries: Int = AppSettings.DEFAULT_HTTP_RETRIES,
    val backoffBaseMs: Long = 250L,
    val userAgent: String = DEFAULT_USER_AGENT,
) {
    init {
        require(timeoutMs > 0L) { "timeoutMs must be positive" }
        require(retries >= 0) { "retries must not be negative" }
        require(backoffBaseMs >= 0L) { "backoffBaseMs must not be negative" }
    }

    companion object {
        const val DEFAULT_USER_AGENT = "Cue/0.1 (https://github.com/RPN17M2G/Cue)"

        fun from(policy: LookupPolicy): NetworkConfig = NetworkConfig(
            timeoutMs = policy.timeoutMs,
            retries = policy.retries,
        )
    }
}
