package com.onekey.blelab

import org.junit.Assert.assertTrue
import org.junit.Test

class AdvertiseErrorTest {
    @Test
    fun explainsEveryLegacyCallbackCode() {
        val explanations = listOf(
            "DATA_TOO_LARGE", "TOO_MANY_ADVERTISERS", "ALREADY_STARTED",
            "INTERNAL_ERROR", "FEATURE_UNSUPPORTED",
        )
        explanations.forEachIndexed { index, expected ->
            val code = index + 1
            val message = describeAdvertiseError(code)
            assertTrue(message.contains("오류 $code"))
            assertTrue(message.contains(expected))
        }
    }

    @Test
    fun preservesUnrecognizedCallbackCode() {
        val message = describeAdvertiseError(42)
        assertTrue(message.contains("오류 42"))
        assertTrue(message.contains("UNKNOWN"))
    }
}
