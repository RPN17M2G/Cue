package io.github.rpn17m2g.cue.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The signature is the one part of the ACRCloud request that cannot be checked
 * by reading the code: a wrong newline, field order or digest produces a valid
 * looking base64 string that the server rejects. The vector below was produced
 * independently with Python's `hmac`/`hashlib`, so a refactor that changes the
 * string to sign fails here instead of on a real device.
 */
class AcrCloudSignatureTest {

    @Test
    fun `signs the documented string with HMAC SHA1`() {
        val signature = AcrCloudSignature.sign(
            accessKey = "ACCESS_KEY",
            accessSecret = "SECRET",
            timestamp = "1700000000",
        )

        assertEquals("aaepYYfreKJFpHHlwJqAxEshcKA=", signature)
    }

    @Test
    fun `timestamp participates in the signature`() {
        val first = AcrCloudSignature.sign("ACCESS_KEY", "SECRET", "1700000000")
        val second = AcrCloudSignature.sign("ACCESS_KEY", "SECRET", "1700000001")

        assertNotEquals(first, second)
    }

    @Test
    fun `secret participates in the signature`() {
        val first = AcrCloudSignature.sign("ACCESS_KEY", "SECRET", "1700000000")
        val second = AcrCloudSignature.sign("ACCESS_KEY", "OTHER", "1700000000")

        assertNotEquals(first, second)
    }
}
