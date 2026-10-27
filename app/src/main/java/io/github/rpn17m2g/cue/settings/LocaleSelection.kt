package io.github.rpn17m2g.cue.settings

/**
 * Locale tag for a saved language choice. `null` means "follow the device",
 * which both AppCompat and the framework express as an empty locale list.
 */
fun AppLanguage.localeTag(): String? = when (this) {
    AppLanguage.SYSTEM -> null
    AppLanguage.ENGLISH -> "en"
    AppLanguage.HEBREW -> "he"
}
