package io.github.rpn17m2g.cue.settings

/**
 * Shape checks for the ACRCloud project credentials.
 *
 * ACRCloud needs three values from the project console: the host, the access
 * key and the access secret. They are secrets like the AcoustID key, so they
 * live in the untracked `local.properties` as build defaults and can be
 * overridden in Settings.
 *
 * The host is the only value with a shape worth checking: it is used as the
 * Retrofit base URL, so anything containing a path, a scheme or whitespace has
 * to be rejected rather than concatenated into a request URL.
 */
object AcrCloudCredentials {

    private val HOST = Regex("^[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?(?::\\d{1,5})?$")

    fun isConfigured(host: String?, accessKey: String?, accessSecret: String?): Boolean =
        baseUrl(host) != null && !accessKey.isNullOrBlank() && !accessSecret.isNullOrBlank()

    /**
     * The `https://host/` base URL Retrofit needs, or null when the value is
     * not a bare host. Accepts a pasted URL and strips the scheme and trailing
     * slash so the console value can be pasted as-is.
     */
    fun baseUrl(host: String?): String? {
        val raw = host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val bare = raw
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        if (!HOST.matches(bare)) return null
        return "https://$bare/"
    }

    /**
     * A blank stored value means "never saved", so the build default applies.
     * This mirrors [AcoustIdKey.orBuildDefault] and keeps an early build from
     * pinning an empty credential forever.
     */
    fun orBuildDefault(stored: String?, buildDefault: String): String {
        val trimmed = stored?.trim()
        return if (!trimmed.isNullOrEmpty()) trimmed else buildDefault.trim()
    }
}
