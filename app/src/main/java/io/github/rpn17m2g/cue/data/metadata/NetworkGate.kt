package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.settings.AppSettings

/** User-controlled switch; when closed no provider touches the network. */
fun interface NetworkGate {
    fun isNetworkEnabled(): Boolean

    companion object {
        val Enabled = NetworkGate { true }
        val Disabled = NetworkGate { false }
    }
}

fun AppSettings.asNetworkGate(): NetworkGate = NetworkGate { networkLookups }

/** Immutable snapshot of the settings that affect online lookups. */
data class LookupPolicy(
    val networkEnabled: Boolean = true,
    val timeoutMs: Long = AppSettings.DEFAULT_HTTP_TIMEOUT_MS,
    val retries: Int = AppSettings.DEFAULT_HTTP_RETRIES,
    val acoustIdKey: String = "",
) {
    val networkGate: NetworkGate get() = NetworkGate { networkEnabled }

    companion object {
        fun from(settings: AppSettings): LookupPolicy = LookupPolicy(
            networkEnabled = settings.networkLookups,
            timeoutMs = settings.httpTimeoutMs,
            retries = settings.httpRetries,
            acoustIdKey = settings.acoustIdKey,
        )
    }
}
