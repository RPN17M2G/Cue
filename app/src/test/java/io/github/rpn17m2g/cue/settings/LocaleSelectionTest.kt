package io.github.rpn17m2g.cue.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocaleSelectionTest {

    @Test
    fun `system follows the device locale`() {
        assertNull(AppLanguage.SYSTEM.localeTag())
    }

    @Test
    fun `explicit languages map to their language tags`() {
        assertEquals("en", AppLanguage.ENGLISH.localeTag())
        assertEquals("he", AppLanguage.HEBREW.localeTag())
    }
}
