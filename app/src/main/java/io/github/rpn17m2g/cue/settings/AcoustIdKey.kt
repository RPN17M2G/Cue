package io.github.rpn17m2g.cue.settings

/**
 * Shape check for an AcoustID application key.
 *
 * The provider does not publish an exact alphabet, but a real key is a long
 * opaque token (the public docs key is 11 characters). A two-character value
 * left behind by an early build is not a key, and treating it as one disables
 * fingerprint lookup forever: the cascade sees a configured key, sends it, and
 * the provider rejects it. Anything that fails this shape is treated as unset,
 * so the build-time key applies instead.
 */
object AcoustIdKey {

    private val SHAPE = Regex("^[A-Za-z0-9_-]{8,40}$")

    fun isPlausible(value: String?): Boolean {
        val trimmed = value?.trim() ?: return false
        return SHAPE.matches(trimmed)
    }

    /**
     * Returns the stored key when it looks real, otherwise [buildDefault]. A
     * blank stored value has always meant "unset", so it follows the same path.
     */
    fun orBuildDefault(stored: String?, buildDefault: String): String {
        val trimmed = stored?.trim()
        return if (isPlausible(trimmed)) trimmed.orEmpty() else buildDefault.trim()
    }
}
